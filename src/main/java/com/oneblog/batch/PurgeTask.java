package com.oneblog.batch;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogRepository;
import com.oneblog.blog.BlogStatus;
import com.oneblog.file.FileStorage;
import com.oneblog.member.User;
import com.oneblog.member.UserRepository;
import com.oneblog.member.UserStatus;

/**
 * 30일 지난 데이터 완전 삭제 (4.5).
 * - 삭제한 글(댓글·좋아요·태그·사진 포함), 삭제한 댓글(살아 있는 답글이 달린 첫 댓글은 남긴다), 삭제 표시한 파일,
 *   글에 붙지 않은 채 30일 지난 업로드 이미지
 * - 폐쇄 30일 지난 블로그: 글·사진·카테고리·태그·구독을 지우고 주소를 비운다. 블로그 행은 남긴다 (D-86)
 * - 탈퇴 30일 지난 회원: 이름·닉네임·전화번호·프로필 사진·소개를 지운다. 글은 "탈퇴한 회원"으로 남는다
 * DB에서 먼저 지우고(트랜잭션), 커밋한 뒤 디스크 파일을 지운다. 파일 삭제가 실패해도 DB는 일관된다.
 */
@Component
public class PurgeTask implements DailyTask {

    static final int RETENTION_DAYS = 30;
    private static final Logger log = LoggerFactory.getLogger(PurgeTask.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final FileStorage storage;
    private final BlogRepository blogRepository;
    private final UserRepository userRepository;

    public PurgeTask(NamedParameterJdbcTemplate jdbc, TransactionTemplate tx, FileStorage storage,
            BlogRepository blogRepository, UserRepository userRepository) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.storage = storage;
        this.blogRepository = blogRepository;
        this.userRepository = userRepository;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public String name() {
        return "30일 지난 데이터 삭제";
    }

    @Override
    public int run(LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(RETENTION_DAYS);
        List<String> files = new ArrayList<>();
        Integer count = tx.execute(status -> {
            int total = 0;
            total += purgeClosedBlogs(cutoff, now, files);
            total += purgePosts(idsOf("SELECT id FROM posts WHERE deleted_at <= :cutoff", cutoff), files);
            total += purgeComments(cutoff);
            total += purgeFiles(cutoff, files);
            total += purgeWithdrawnUsers(cutoff, now, files);
            return total;
        });
        for (String name : files) {
            try {
                storage.delete(name);
            } catch (IOException | RuntimeException e) {
                log.warn("파일을 지우지 못했습니다: {}", name, e);
            }
        }
        return count == null ? 0 : count;
    }

    private int purgeClosedBlogs(LocalDateTime cutoff, LocalDateTime now, List<String> files) {
        List<Blog> blogs = blogRepository.findClosedBefore(BlogStatus.CLOSED, cutoff);
        for (Blog blog : blogs) {
            MapSqlParameterSource args = new MapSqlParameterSource("blogId", blog.getId());
            purgePosts(jdbc.queryForList("SELECT id FROM posts WHERE blog_id = :blogId", args, Long.class), files);
            jdbc.update("DELETE FROM categories WHERE blog_id = :blogId", args);
            jdbc.update("DELETE FROM blog_tags WHERE blog_id = :blogId", args);
            jdbc.update("DELETE FROM blog_subscriptions WHERE blog_id = :blogId", args);
            blog.purge(now);
        }
        blogRepository.saveAllAndFlush(blogs);
        return blogs.size();
    }

    /** 글을 완전히 지운다. 답글 → 첫 댓글 → 글 순서 (댓글끼리의 외래 키 때문). */
    private int purgePosts(List<Long> postIds, List<String> files) {
        if (postIds.isEmpty()) {
            return 0;
        }
        MapSqlParameterSource args = new MapSqlParameterSource("ids", postIds);
        files.addAll(jdbc.queryForList("SELECT stored_name FROM files WHERE post_id IN (:ids)", args, String.class));
        jdbc.update("DELETE FROM files WHERE post_id IN (:ids)", args);
        jdbc.update("DELETE FROM comments WHERE post_id IN (:ids) AND parent_id IS NOT NULL", args);
        jdbc.update("DELETE FROM comments WHERE post_id IN (:ids)", args);
        return jdbc.update("DELETE FROM posts WHERE id IN (:ids)", args);
    }

    private int purgeComments(LocalDateTime cutoff) {
        MapSqlParameterSource args = new MapSqlParameterSource("cutoff", cutoff);
        int count = jdbc.update("DELETE FROM comments WHERE deleted_at <= :cutoff AND parent_id IS NOT NULL", args);
        List<Long> roots = jdbc.queryForList("""
                SELECT c.id FROM comments c
                WHERE c.deleted_at <= :cutoff AND c.parent_id IS NULL
                  AND NOT EXISTS (SELECT 1 FROM comments r WHERE r.parent_id = c.id)
                """, args, Long.class);
        if (!roots.isEmpty()) {
            count += jdbc.update("DELETE FROM comments WHERE id IN (:ids)", new MapSqlParameterSource("ids", roots));
        }
        return count;
    }

    private int purgeFiles(LocalDateTime cutoff, List<String> files) {
        String where = """
                FROM files WHERE deleted_at <= :cutoff
                   OR (post_id IS NULL AND purpose = 'POST' AND created_at <= :cutoff)""";
        MapSqlParameterSource args = new MapSqlParameterSource("cutoff", cutoff);
        files.addAll(jdbc.queryForList("SELECT stored_name " + where, args, String.class));
        return jdbc.update("DELETE " + where, args);
    }

    private int purgeWithdrawnUsers(LocalDateTime cutoff, LocalDateTime now, List<String> files) {
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM users WHERE status = 'WITHDRAWN' AND withdrawn_at <= :cutoff AND deleted_at IS NULL
                """, new MapSqlParameterSource("cutoff", cutoff), Long.class);
        if (ids.isEmpty()) {
            return 0;
        }
        MapSqlParameterSource args = new MapSqlParameterSource("ids", ids);
        files.addAll(jdbc.queryForList("SELECT stored_name FROM files WHERE user_id IN (:ids) AND purpose = 'PROFILE'",
                args, String.class));
        jdbc.update("DELETE FROM files WHERE user_id IN (:ids) AND purpose = 'PROFILE'", args);
        List<User> users = userRepository.findAllById(ids);
        users.stream().filter(u -> u.getStatus() == UserStatus.WITHDRAWN).forEach(u -> u.purgePersonalInfo(now));
        userRepository.saveAllAndFlush(users);
        return users.size();
    }

    private List<Long> idsOf(String sql, LocalDateTime cutoff) {
        return jdbc.queryForList(sql, new MapSqlParameterSource("cutoff", cutoff), Long.class);
    }
}
