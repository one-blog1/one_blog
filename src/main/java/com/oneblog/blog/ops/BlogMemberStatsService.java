package com.oneblog.blog.ops;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogAccessService;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.PageParams;
import com.oneblog.common.web.Times;

/**
 * 블로그장 전용 멤버 더보기 창 (BLG-06, BLG-13, D-112).
 * 멤버마다 이 블로그에서 쓴 글 수·댓글 수·경고 횟수를 보고, 그 회원의 글·댓글 목록으로 들어간다.
 * 블로그장만 볼 수 있다(부블로그장은 멤버 관리 권한이 있어도 못 본다).
 */
@Service
public class BlogMemberStatsService {

    private final BlogAccessService accessService;
    private final BlogManageService manageService;
    private final NamedParameterJdbcTemplate jdbc;

    public BlogMemberStatsService(BlogAccessService accessService, BlogManageService manageService,
            NamedParameterJdbcTemplate jdbc) {
        this.accessService = accessService;
        this.manageService = manageService;
        this.jdbc = jdbc;
    }

    public record MemberStat(Long userId, String nickname, String role, long postCount, long commentCount,
            long warningCount, OffsetDateTime joinedAt) {
    }

    public record MemberPost(Long id, String title, boolean notice, boolean hidden, OffsetDateTime createdAt) {
    }

    public record MemberComment(Long id, Long postId, String postTitle, String content, boolean hidden,
            OffsetDateTime createdAt) {
    }

    public record Page<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

        static <T> Page<T> of(List<T> items, PageParams params, long total) {
            int totalPages = params.totalPages(total);
            return new Page<>(items, params.page(), params.size(), total, totalPages);
        }
    }

    @Transactional(readOnly = true)
    public Page<MemberStat> members(String slug, AuthenticatedUser principal, PageParams params) {
        Blog blog = ownerBlog(slug, principal);
        MapSqlParameterSource args = args(blog, params);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM blog_members WHERE blog_id = :blogId AND status = 'ACTIVE'",
                args, Long.class);
        List<MemberStat> rows = jdbc.query("""
                SELECT u.id, u.nickname, m.role, m.joined_at,
                       (SELECT COUNT(*) FROM posts p WHERE p.blog_id = m.blog_id AND p.user_id = m.user_id
                          AND p.deleted_at IS NULL AND p.author_detached = 0),
                       (SELECT COUNT(*) FROM comments c JOIN posts p ON p.id = c.post_id
                          WHERE p.blog_id = m.blog_id AND c.user_id = m.user_id AND c.deleted_at IS NULL
                            AND p.deleted_at IS NULL),
                       (SELECT COUNT(*) FROM sanctions s WHERE s.blog_id = m.blog_id AND s.user_id = m.user_id
                          AND s.type = 'WARNING')
                FROM blog_members m JOIN users u ON u.id = m.user_id
                WHERE m.blog_id = :blogId AND m.status = 'ACTIVE'
                ORDER BY FIELD(m.role, 'OWNER', 'SUB_OWNER', 'MEMBER'), m.joined_at ASC, m.id ASC
                LIMIT :limit OFFSET :offset
                """, args, (rs, i) -> new MemberStat(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(5),
                        rs.getLong(6), rs.getLong(7), Times.toOffset(rs.getTimestamp(4).toLocalDateTime())));
        return Page.of(rows, params, total == null ? 0 : total);
    }

    /** 그 회원이 이 블로그에 쓴 글 (지운 글 제외, 최신순). */
    @Transactional(readOnly = true)
    public Page<MemberPost> posts(String slug, Long userId, AuthenticatedUser principal, PageParams params) {
        Blog blog = ownerBlog(slug, principal);
        MapSqlParameterSource args = args(blog, params).addValue("userId", userId);
        String where = " FROM posts p WHERE p.blog_id = :blogId AND p.user_id = :userId AND p.deleted_at IS NULL"
                + " AND p.author_detached = 0";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, args, Long.class);
        List<MemberPost> rows = jdbc.query("SELECT p.id, p.title, p.post_type, p.is_hidden, p.created_at" + where
                + " ORDER BY p.created_at DESC, p.id DESC LIMIT :limit OFFSET :offset", args,
                (rs, i) -> new MemberPost(rs.getLong(1), rs.getString(2), "BLOG_NOTICE".equals(rs.getString(3)),
                        rs.getBoolean(4), Times.toOffset(rs.getTimestamp(5).toLocalDateTime())));
        return Page.of(rows, params, total == null ? 0 : total);
    }

    /** 그 회원이 이 블로그의 글에 단 댓글 (지운 댓글·지운 글 제외, 최신순). 내용은 앞부분만. */
    @Transactional(readOnly = true)
    public Page<MemberComment> comments(String slug, Long userId, AuthenticatedUser principal, PageParams params) {
        Blog blog = ownerBlog(slug, principal);
        MapSqlParameterSource args = args(blog, params).addValue("userId", userId);
        String where = " FROM comments c JOIN posts p ON p.id = c.post_id WHERE p.blog_id = :blogId"
                + " AND c.user_id = :userId AND c.deleted_at IS NULL AND p.deleted_at IS NULL";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, args, Long.class);
        List<MemberComment> rows = jdbc.query("SELECT c.id, p.id, p.title, LEFT(c.content, 120), c.is_hidden,"
                + " c.created_at" + where + " ORDER BY c.created_at DESC, c.id DESC LIMIT :limit OFFSET :offset", args,
                (rs, i) -> new MemberComment(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getBoolean(5), Times.toOffset(rs.getTimestamp(6).toLocalDateTime())));
        return Page.of(rows, params, total == null ? 0 : total);
    }

    private Blog ownerBlog(String slug, AuthenticatedUser principal) {
        Blog blog = accessService.check(slug, null, principal.id()).blog();
        manageService.requireOwner(blog, principal);
        return blog;
    }

    private static MapSqlParameterSource args(Blog blog, PageParams params) {
        return new MapSqlParameterSource("blogId", blog.getId()).addValue("limit", params.size())
                .addValue("offset", (long) params.zeroBasedPage() * params.size());
    }
}
