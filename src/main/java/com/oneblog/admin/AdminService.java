package com.oneblog.admin;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogRepository;
import com.oneblog.comment.Comment;
import com.oneblog.comment.CommentRepository;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.text.Masking;
import com.oneblog.common.web.ApiException;
import com.oneblog.common.web.PageParams;
import com.oneblog.common.web.Times;
import com.oneblog.post.DeletedBy;
import com.oneblog.post.Post;
import com.oneblog.post.PostRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 메인 관리자 1차 (ADM-01 회원 조회, ADM-02 블로그 조회·숨김, ADM-03 글·댓글 숨김·삭제, ADM-05 통계, ADM-06 기록).
 * 관리자는 회원을 정지·탈퇴시키지 않는다 (D-44). 개인정보는 기본으로 가리고, "전체 보기"를 누르면 기록을 남긴다 (4.4).
 * 목록 조회는 바인딩 파라미터를 쓰는 SQL로 한다 (constitution III).
 */
@Service
public class AdminService {

    private final NamedParameterJdbcTemplate jdbc;
    private final AdminActionLogger actionLogger;
    private final AdminActionRepository actionRepository;
    private final BlogRepository blogRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final List<AdminListener> listeners;

    public AdminService(NamedParameterJdbcTemplate jdbc, AdminActionLogger actionLogger,
            AdminActionRepository actionRepository, BlogRepository blogRepository, PostRepository postRepository,
            CommentRepository commentRepository, List<AdminListener> listeners) {
        this.jdbc = jdbc;
        this.actionLogger = actionLogger;
        this.actionRepository = actionRepository;
        this.blogRepository = blogRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.listeners = listeners;
    }

    // ── 회원 (ADM-01) ─────────────────────────────────────

    public record UserRow(Long id, String email, String name, String nickname, String phone, String status,
            String role, int ownedBlogs, int joinedBlogs, OffsetDateTime createdAt) {
    }

    /** 이메일·이름·닉네임으로 찾는다. 이메일·전화번호는 가려서 보여준다 (4.4). */
    @Transactional(readOnly = true)
    public AdminPage<UserRow> users(String q, PageParams params) {
        MapSqlParameterSource p = new MapSqlParameterSource("q", AdminQueries.likePattern(q))
                .addValue("limit", params.size()).addValue("offset", (long) params.zeroBasedPage() * params.size());
        String where = """
                FROM users u WHERE u.role = 'USER'
                  AND (u.email LIKE :q OR u.name LIKE :q OR u.nickname LIKE :q)
                """;
        long total = jdbc.queryForObject("SELECT COUNT(*) " + where, p, Long.class);
        List<UserRow> rows = jdbc.query("""
                SELECT u.id, u.email, u.name, u.nickname, u.phone, u.status, u.role, u.created_at,
                  (SELECT COUNT(*) FROM blog_members m WHERE m.user_id = u.id AND m.status = 'ACTIVE' AND m.role = 'OWNER') owned,
                  (SELECT COUNT(*) FROM blog_members m WHERE m.user_id = u.id AND m.status = 'ACTIVE' AND m.role <> 'OWNER') joined
                """ + where + " ORDER BY u.created_at DESC, u.id DESC LIMIT :limit OFFSET :offset", p,
                (rs, i) -> new UserRow(rs.getLong("id"), Masking.email(rs.getString("email")), rs.getString("name"),
                        rs.getString("nickname"), Masking.phone(rs.getString("phone")), rs.getString("status"),
                        rs.getString("role"), rs.getInt("owned"), rs.getInt("joined"),
                        Times.toOffset(rs.getTimestamp("created_at").toLocalDateTime())));
        return AdminPage.of(rows, params.page(), params.size(), total);
    }

    public record UserDetail(UserRow user, List<Map<String, Object>> blogs) {
    }

    @Transactional(readOnly = true)
    public UserDetail user(Long userId) {
        List<UserRow> found = jdbc.query("""
                SELECT u.id, u.email, u.name, u.nickname, u.phone, u.status, u.role, u.created_at,
                  (SELECT COUNT(*) FROM blog_members m WHERE m.user_id = u.id AND m.status = 'ACTIVE' AND m.role = 'OWNER') owned,
                  (SELECT COUNT(*) FROM blog_members m WHERE m.user_id = u.id AND m.status = 'ACTIVE' AND m.role <> 'OWNER') joined
                FROM users u WHERE u.id = :id AND u.role = 'USER'
                """, new MapSqlParameterSource("id", userId),
                (rs, i) -> new UserRow(rs.getLong("id"), Masking.email(rs.getString("email")), rs.getString("name"),
                        rs.getString("nickname"), Masking.phone(rs.getString("phone")), rs.getString("status"),
                        rs.getString("role"), rs.getInt("owned"), rs.getInt("joined"),
                        Times.toOffset(rs.getTimestamp("created_at").toLocalDateTime())));
        if (found.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "회원을 찾을 수 없습니다.");
        }
        List<Map<String, Object>> blogs = jdbc.queryForList("""
                SELECT b.id, b.slug, b.name, b.status, m.role, m.status AS member_status
                FROM blog_members m JOIN blogs b ON b.id = m.blog_id
                WHERE m.user_id = :id ORDER BY m.joined_at DESC
                """, new MapSqlParameterSource("id", userId));
        return new UserDetail(found.get(0), blogs);
    }

    public record PersonalInfo(String email, String name, String phone) {
    }

    /** 개인정보 전체 보기. 누른 기록을 남긴다 (4.4, ADM-06). */
    @Transactional
    public PersonalInfo revealUser(Long userId, AuthenticatedUser admin, HttpServletRequest request) {
        List<PersonalInfo> found = jdbc.query("SELECT email, name, phone FROM users WHERE id = :id AND role = 'USER'",
                new MapSqlParameterSource("id", userId),
                (rs, i) -> new PersonalInfo(rs.getString("email"), rs.getString("name"),
                        Masking.formatPhone(rs.getString("phone"))));
        if (found.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "회원을 찾을 수 없습니다.");
        }
        actionLogger.log(admin, "VIEW_PERSONAL_INFO", "USER", userId, "개인정보 전체 보기", request);
        return found.get(0);
    }

    // ── 블로그 (ADM-02) ───────────────────────────────────

    public record BlogRow(Long id, String slug, String name, String visibility, String status, boolean hidden,
            String ownerNickname, int memberCount, long postCount, OffsetDateTime createdAt) {
    }

    @Transactional(readOnly = true)
    public AdminPage<BlogRow> blogs(String q, PageParams params) {
        MapSqlParameterSource p = new MapSqlParameterSource("q", AdminQueries.likePattern(q))
                .addValue("limit", params.size()).addValue("offset", (long) params.zeroBasedPage() * params.size());
        String where = " FROM blogs b WHERE b.deleted_at IS NULL AND (b.name LIKE :q OR b.slug LIKE :q) ";
        long total = jdbc.queryForObject("SELECT COUNT(*)" + where, p, Long.class);
        List<BlogRow> rows = jdbc.query("""
                SELECT b.id, b.slug, b.name, b.visibility, b.status, b.is_hidden, b.member_count, b.created_at,
                  (SELECT u.nickname FROM blog_members m JOIN users u ON u.id = m.user_id
                    WHERE m.blog_id = b.id AND m.role = 'OWNER' AND m.status = 'ACTIVE' LIMIT 1) owner_nickname,
                  (SELECT COUNT(*) FROM posts p WHERE p.blog_id = b.id AND p.deleted_at IS NULL) post_count
                """ + where + " ORDER BY b.created_at DESC, b.id DESC LIMIT :limit OFFSET :offset", p,
                (rs, i) -> new BlogRow(rs.getLong("id"), rs.getString("slug"), rs.getString("name"),
                        rs.getString("visibility"), rs.getString("status"), rs.getBoolean("is_hidden"),
                        rs.getString("owner_nickname"), rs.getInt("member_count"), rs.getLong("post_count"),
                        Times.toOffset(rs.getTimestamp("created_at").toLocalDateTime())));
        return AdminPage.of(rows, params.page(), params.size(), total);
    }

    @Transactional
    public void hideBlog(Long blogId, boolean hidden, String reason, AuthenticatedUser admin,
            HttpServletRequest request) {
        Blog blog = blogRepository.findById(blogId).filter(Blog::isOpen)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BLOG_NOT_FOUND", "블로그를 찾을 수 없습니다."));
        blog.hide(hidden);
        actionLogger.log(admin, hidden ? "BLOG_HIDE" : "BLOG_UNHIDE", "BLOG", blogId, reason, request);
    }

    // ── 글·댓글 (ADM-03) ──────────────────────────────────

    public record PostRow(Long id, String blogSlug, String blogName, String title, String authorNickname,
            String postType, boolean hidden, boolean deleted, OffsetDateTime createdAt) {
    }

    @Transactional(readOnly = true)
    public AdminPage<PostRow> posts(String q, PageParams params) {
        MapSqlParameterSource p = new MapSqlParameterSource("q", AdminQueries.likePattern(q))
                .addValue("limit", params.size()).addValue("offset", (long) params.zeroBasedPage() * params.size());
        String where = """
                 FROM posts p LEFT JOIN blogs b ON b.id = p.blog_id JOIN users u ON u.id = p.user_id
                 WHERE p.title LIKE :q OR u.nickname LIKE :q
                """;
        long total = jdbc.queryForObject("SELECT COUNT(*)" + where, p, Long.class);
        List<PostRow> rows = jdbc.query("SELECT p.id, b.slug, b.name, p.title, u.nickname, p.post_type, p.is_hidden,"
                + " p.deleted_at IS NOT NULL AS deleted, p.created_at" + where
                + " ORDER BY p.created_at DESC, p.id DESC LIMIT :limit OFFSET :offset", p,
                (rs, i) -> new PostRow(rs.getLong("id"), rs.getString("slug"), rs.getString("name"),
                        rs.getString("title"), rs.getString("nickname"), rs.getString("post_type"),
                        rs.getBoolean("is_hidden"), rs.getBoolean("deleted"),
                        Times.toOffset(rs.getTimestamp("created_at").toLocalDateTime())));
        return AdminPage.of(rows, params.page(), params.size(), total);
    }

    @Transactional
    public void hidePost(Long postId, boolean hidden, String reason, AuthenticatedUser admin,
            HttpServletRequest request) {
        Post post = postRepository.findById(postId).filter(pp -> !pp.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "글을 찾을 수 없습니다."));
        post.hide(hidden);
        actionLogger.log(admin, hidden ? "POST_HIDE" : "POST_UNHIDE", "POST", postId, reason, request);
    }

    /** 관리자 글 삭제 (ADM-03, D-07). 작성자 알림은 AdminListener(011). */
    @Transactional
    public void deletePost(Long postId, String reason, AuthenticatedUser admin, HttpServletRequest request) {
        Post post = postRepository.findById(postId).filter(pp -> !pp.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "글을 찾을 수 없습니다."));
        post.delete(DeletedBy.ADMIN);
        actionLogger.log(admin, "POST_DELETE", "POST", postId, reason, request);
        for (AdminListener listener : listeners) {
            listener.postDeletedByAdmin(post, reason);
        }
    }

    /** blogSlug는 관리자 화면의 "바로가기" 주소용. 메인 공지에 달린 댓글이면 null. */
    public record CommentRow(Long id, Long postId, String blogSlug, String postTitle, String content,
            String authorNickname, boolean hidden, boolean deleted, OffsetDateTime createdAt) {
    }

    @Transactional(readOnly = true)
    public AdminPage<CommentRow> comments(String q, PageParams params) {
        MapSqlParameterSource p = new MapSqlParameterSource("q", AdminQueries.likePattern(q))
                .addValue("limit", params.size()).addValue("offset", (long) params.zeroBasedPage() * params.size());
        String where = """
                 FROM comments c JOIN posts p ON p.id = c.post_id JOIN users u ON u.id = c.user_id
                 LEFT JOIN blogs b ON b.id = p.blog_id
                 WHERE c.content LIKE :q OR u.nickname LIKE :q
                """;
        long total = jdbc.queryForObject("SELECT COUNT(*)" + where, p, Long.class);
        List<CommentRow> rows = jdbc.query("SELECT c.id, c.post_id, b.slug, p.title, c.content, u.nickname,"
                + " c.is_hidden, c.deleted_at IS NOT NULL AS deleted, c.created_at" + where
                + " ORDER BY c.created_at DESC, c.id DESC LIMIT :limit OFFSET :offset", p,
                (rs, i) -> new CommentRow(rs.getLong("id"), rs.getLong("post_id"), rs.getString("slug"),
                        rs.getString("title"),
                        rs.getString("content"), rs.getString("nickname"), rs.getBoolean("is_hidden"),
                        rs.getBoolean("deleted"), Times.toOffset(rs.getTimestamp("created_at").toLocalDateTime())));
        return AdminPage.of(rows, params.page(), params.size(), total);
    }

    @Transactional
    public void hideComment(Long commentId, boolean hidden, String reason, AuthenticatedUser admin,
            HttpServletRequest request) {
        Comment comment = commentRepository.findById(commentId).filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다."));
        comment.hide(hidden);
        actionLogger.log(admin, hidden ? "COMMENT_HIDE" : "COMMENT_UNHIDE", "COMMENT", commentId, reason, request);
    }

    @Transactional
    public void deleteComment(Long commentId, String reason, AuthenticatedUser admin, HttpServletRequest request) {
        Comment comment = commentRepository.findById(commentId).filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다."));
        comment.delete();
        postRepository.addCommentCount(comment.getPostId(), -1);
        actionLogger.log(admin, "COMMENT_DELETE", "COMMENT", commentId, reason, request);
        for (AdminListener listener : listeners) {
            listener.commentDeletedByAdmin(comment, reason);
        }
    }

    // ── 통계 (ADM-05) ─────────────────────────────────────

    public record DailyCount(LocalDate date, long signups, long posts) {
    }

    public record Stats(long users, long blogs, long posts, List<DailyCount> daily) {
    }

    /** 회원 수, 블로그 수, 글 수와 최근 days일의 일별 가입·글 수. */
    @Transactional(readOnly = true)
    public Stats stats(int days) {
        int range = Math.max(1, Math.min(days, 90));
        MapSqlParameterSource p = new MapSqlParameterSource("from", LocalDate.now().minusDays(range - 1L));
        long users = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'USER' AND status = 'ACTIVE'", p,
                Long.class);
        long blogs = jdbc.queryForObject("SELECT COUNT(*) FROM blogs WHERE status <> 'CLOSED' AND deleted_at IS NULL", p,
                Long.class);
        long posts = jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE deleted_at IS NULL", p, Long.class);
        Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
        for (int i = 0; i < range; i++) {
            byDay.put(LocalDate.now().minusDays(range - 1L - i), new long[2]);
        }
        jdbc.query("SELECT DATE(created_at) d, COUNT(*) c FROM users WHERE role = 'USER' AND created_at >= :from"
                + " GROUP BY DATE(created_at)", p, rs -> {
                    long[] v = byDay.get(rs.getDate("d").toLocalDate());
                    if (v != null) {
                        v[0] = rs.getLong("c");
                    }
                });
        jdbc.query("SELECT DATE(created_at) d, COUNT(*) c FROM posts WHERE created_at >= :from GROUP BY DATE(created_at)",
                p, rs -> {
                    long[] v = byDay.get(rs.getDate("d").toLocalDate());
                    if (v != null) {
                        v[1] = rs.getLong("c");
                    }
                });
        List<DailyCount> daily = new ArrayList<>();
        byDay.forEach((d, v) -> daily.add(new DailyCount(d, v[0], v[1])));
        return new Stats(users, blogs, posts, daily);
    }

    // ── 활동 기록 (ADM-06) ────────────────────────────────

    public record ActionRow(Long id, String adminLoginId, String actionType, String targetType, Long targetId,
            String detail, String ipAddress, OffsetDateTime createdAt) {
    }

    @Transactional(readOnly = true)
    public AdminPage<ActionRow> actions(PageParams params) {
        MapSqlParameterSource p = new MapSqlParameterSource("limit", params.size())
                .addValue("offset", (long) params.zeroBasedPage() * params.size());
        long total = actionRepository.count();
        List<ActionRow> rows = jdbc.query("""
                SELECT a.id, u.login_id, a.action_type, a.target_type, a.target_id, a.detail, a.ip_address, a.created_at
                FROM admin_actions a JOIN users u ON u.id = a.admin_id
                ORDER BY a.created_at DESC, a.id DESC LIMIT :limit OFFSET :offset
                """, p,
                (rs, i) -> new ActionRow(rs.getLong("id"), rs.getString("login_id"), rs.getString("action_type"),
                        rs.getString("target_type"), rs.getLong("target_id"), rs.getString("detail"),
                        rs.getString("ip_address"), Times.toOffset(rs.getTimestamp("created_at").toLocalDateTime())));
        return AdminPage.of(rows, params.page(), params.size(), total);
    }
}
