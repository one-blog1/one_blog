package com.oneblog.report;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.admin.AdminActionLogger;
import com.oneblog.admin.AdminPage;
import com.oneblog.admin.AdminService;
import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogAccessService;
import com.oneblog.blog.BlogMember;
import com.oneblog.blog.BlogMemberRepository;
import com.oneblog.blog.BlogRepository;
import com.oneblog.comment.Comment;
import com.oneblog.comment.CommentRepository;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.security.RateLimiter;
import com.oneblog.common.web.ApiException;
import com.oneblog.common.web.ErrorResponse;
import com.oneblog.common.web.PageParams;
import com.oneblog.common.web.Times;
import com.oneblog.member.User;
import com.oneblog.member.UserRepository;
import com.oneblog.member.UserRole;
import com.oneblog.member.ValidationFailedException;
import com.oneblog.notification.NotificationService;
import com.oneblog.notification.NotificationType;
import com.oneblog.post.Post;
import com.oneblog.post.PostRepository;
import com.oneblog.sanction.OwnerSanctionService;
import com.oneblog.sanction.SanctionService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 신고 (SOC-06, ADM-04, BLG-13, D-45, D-85, D-95, 6.6).
 * - 대상: 회원(블로그 안 USER, 메인 프로필 PROFILE), 블로그, 글, 댓글. 사유를 골라 신고한다
 * - 접수처: 블로그 안의 멤버·글·댓글 → 그 블로그(blog_id). 블로그 자체, 블로그장, 블로그장이 쓴 글·댓글,
 *   메인 프로필 회원 → 메인 관리자(blog_id NULL)
 * - 같은 사람이 같은 대상은 2주에 한 번. 자기 자신·자기 글은 신고할 수 없다. 관리자는 신고하지 않는다
 * - 처리: 블로그(문제 없음·경고·정지·강제 퇴장), 관리자(문제 없음·글/댓글 삭제·블로그장 경고·블로그장 강퇴·블로그 폐쇄).
 *   신고 대상 본인은 처리할 수 없다. 같은 대상의 대기 신고는 함께 처리되고 신고자마다 결과 알림이 간다
 */
@Service
public class ReportService {

    static final int REPORT_AGAIN_AFTER_DAYS = 14;
    static final Map<String, String> RESULT_LABELS = Map.of(
            "NO_ISSUE", "문제 없음", "WARNING", "경고", "SUSPENSION", "정지", "KICK", "강제 퇴장",
            "CONTENT_DELETED", "삭제", "OWNER_WARNING", "블로그장 경고", "OWNER_REVOKED", "블로그장 권한 박탈",
            "BLOG_CLOSED", "블로그 폐쇄");

    private final NamedParameterJdbcTemplate jdbc;
    private final BlogAccessService accessService;
    private final BlogRepository blogRepository;
    private final BlogMemberRepository memberRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final SanctionService sanctionService;
    private final OwnerSanctionService ownerSanctionService;
    private final AdminService adminService;
    private final AdminActionLogger actionLogger;
    private final NotificationService notifications;
    private final RateLimiter rateLimiter;

    public ReportService(NamedParameterJdbcTemplate jdbc, BlogAccessService accessService,
            BlogRepository blogRepository, BlogMemberRepository memberRepository, PostRepository postRepository,
            CommentRepository commentRepository, UserRepository userRepository, SanctionService sanctionService,
            OwnerSanctionService ownerSanctionService, AdminService adminService, AdminActionLogger actionLogger,
            NotificationService notifications, RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.jdbc = jdbc;
        this.accessService = accessService;
        this.blogRepository = blogRepository;
        this.memberRepository = memberRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.sanctionService = sanctionService;
        this.ownerSanctionService = ownerSanctionService;
        this.adminService = adminService;
        this.actionLogger = actionLogger;
        this.notifications = notifications;
    }

    public record ReportRequest(String targetType, Long targetId, String nickname, String blogSlug, String key,
            String reason, String detail) {
    }

    public record ResolveRequest(String result, Integer days, String reason) {
    }

    public record ReportItem(Long id, String targetType, Long targetId, Long targetUserId, String targetNickname,
            String snapshot, String reason, String reasonLabel, String detail, String reporterNickname, String status,
            String result, OffsetDateTime createdAt) {
    }

    /** 신고 대상 정리 결과. queueBlogId가 null이면 메인 관리자가 처리한다. */
    private record Target(String type, Long id, Long queueBlogId, Long authorId, Map<String, Object> snapshot) {
    }

    @Transactional
    public Long create(AuthenticatedUser principal, ReportRequest request) {
        if (principal.role() == UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_NOT_ALLOWED", "관리자 계정은 신고하지 않습니다.");
        }
        // 신고를 마구 넣지 못하게 한다 (D-80)
        rateLimiter.check("report", String.valueOf(principal.id()), 20, Duration.ofHours(1));
        ReportReason reason = parseReason(request.reason());
        String detail = request.detail() == null ? null : request.detail().strip();
        if (detail != null && detail.codePointCount(0, detail.length()) > 500) {
            throw new ValidationFailedException(List.of(new ErrorResponse.FieldError("detail", "자세한 내용은 500자까지 쓸 수 있습니다.")));
        }
        Target target = resolveTarget(principal, request);
        if (target.authorId() != null && target.authorId().equals(principal.id())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_REPORT_SELF", "자기 자신이나 내 글은 신고할 수 없습니다.");
        }
        // 같은 회원의 신고를 줄 세워 2주 규칙을 동시에 넘지 않게 한다
        userRepository.findForUpdateById(principal.id());
        MapSqlParameterSource args = new MapSqlParameterSource()
                .addValue("reporterId", principal.id()).addValue("type", target.type()).addValue("targetId", target.id())
                .addValue("since", LocalDateTime.now().minusDays(REPORT_AGAIN_AFTER_DAYS));
        Integer recent = jdbc.queryForObject("""
                SELECT COUNT(*) FROM reports WHERE reporter_id = :reporterId AND target_type = :type
                  AND target_id = :targetId AND created_at >= :since
                """, args, Integer.class);
        if (recent != null && recent > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REPORTED", "같은 대상은 2주에 한 번만 신고할 수 있습니다.");
        }
        Map<String, Object> snapshot = new LinkedHashMap<>(target.snapshot());
        snapshot.put("authorId", target.authorId());
        args.addValue("blogId", target.queueBlogId()).addValue("snapshot", SnapshotJson.of(snapshot))
                .addValue("reason", reason.name()).addValue("detail", detail == null || detail.isEmpty() ? null : detail);
        jdbc.update("""
                INSERT INTO reports (blog_id, reporter_id, target_type, target_id, target_snapshot, reason_code, reason_detail)
                VALUES (:blogId, :reporterId, :type, :targetId, :snapshot, :reason, :detail)
                """, args);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", new MapSqlParameterSource(), Long.class);
    }

    private Target resolveTarget(AuthenticatedUser principal, ReportRequest request) {
        String type = request.targetType() == null ? "" : request.targetType();
        switch (type) {
            case "POST" -> {
                Post post = postRepository.findById(nonNull(request.targetId()))
                        .filter(p -> p.isVisible() && p.getBlogId() != null).orElseThrow(ReportService::notFound);
                Blog blog = blogRepository.findById(post.getBlogId()).orElseThrow(ReportService::notFound);
                accessService.check(blog, request.key(), principal.id());
                Map<String, Object> snap = base(blog);
                snap.put("title", post.getTitle());
                snap.put("content", SnapshotJson.shorten(post.getContent(), 1000));
                snap.put("author", nickname(post.getUserId()));
                return new Target(type, post.getId(), queue(blog, post.getUserId()), post.getUserId(), snap);
            }
            case "COMMENT" -> {
                Comment comment = commentRepository.findById(nonNull(request.targetId()))
                        .filter(c -> !c.isDeleted() && !c.isHidden()).orElseThrow(ReportService::notFound);
                Post post = postRepository.findById(comment.getPostId()).filter(Post::isVisible)
                        .orElseThrow(ReportService::notFound);
                Blog blog = blogRepository.findById(post.getBlogId()).orElseThrow(ReportService::notFound);
                accessService.check(blog, request.key(), principal.id());
                Map<String, Object> snap = base(blog);
                snap.put("postId", post.getId());
                snap.put("postTitle", post.getTitle());
                snap.put("content", SnapshotJson.shorten(comment.getContent(), 500));
                snap.put("author", nickname(comment.getUserId()));
                return new Target(type, comment.getId(), queue(blog, comment.getUserId()), comment.getUserId(), snap);
            }
            case "USER" -> {
                Blog blog = accessService.check(request.blogSlug(), request.key(), principal.id()).blog();
                User user = member(request.nickname());
                if (memberRepository.findActive(blog.getId(), user.getId()).isEmpty()) {
                    throw notFound();
                }
                Map<String, Object> snap = base(blog);
                snap.put("author", user.getNickname());
                return new Target(type, user.getId(), queue(blog, user.getId()), user.getId(), snap);
            }
            case "PROFILE" -> {
                User user = member(request.nickname());
                Map<String, Object> snap = new LinkedHashMap<>();
                snap.put("author", user.getNickname());
                snap.put("bio", user.getBio());
                return new Target(type, user.getId(), null, user.getId(), snap);
            }
            case "BLOG" -> {
                Blog blog = accessService.check(request.blogSlug(), request.key(), principal.id()).blog();
                Map<String, Object> snap = base(blog);
                snap.put("description", SnapshotJson.shorten(blog.getDescription(), 500));
                Long ownerId = memberRepository.findOwnerId(blog.getId());
                snap.put("author", ownerId == null ? null : nickname(ownerId));
                return new Target(type, blog.getId(), null, ownerId, snap);
            }
            default -> throw new ValidationFailedException(List.of(
                    new ErrorResponse.FieldError("targetType", "신고할 대상을 골라 주세요.")));
        }
    }

    /** 블로그장이 쓴 것·블로그장 본인이면 메인 관리자, 아니면 그 블로그 (D-95). */
    private Long queue(Blog blog, Long authorId) {
        Long ownerId = memberRepository.findOwnerId(blog.getId());
        return authorId.equals(ownerId) ? null : blog.getId();
    }

    private Map<String, Object> base(Blog blog) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("blogId", blog.getId());
        snap.put("blogSlug", blog.getSlug());
        snap.put("blogName", blog.getName());
        return snap;
    }

    // ---- 블로그장 처리 (BLG-13) ----

    @Transactional(readOnly = true)
    public List<ReportItem> listForBlog(String slug, AuthenticatedUser principal, String status) {
        Blog blog = requireBlogManager(slug, principal);
        MapSqlParameterSource args = new MapSqlParameterSource("blogId", blog.getId())
                .addValue("status", "RESOLVED".equals(status) ? "RESOLVED" : "PENDING")
                .addValue("me", principal.id());
        // 신고 대상 본인은 그 신고를 볼 수도 처리할 수도 없다 (D-95)
        return jdbc.query(SELECT + """
                WHERE r.blog_id = :blogId AND r.status = :status
                  AND COALESCE(CAST(JSON_EXTRACT(r.target_snapshot, '$.authorId') AS UNSIGNED), 0) <> :me
                ORDER BY r.created_at DESC LIMIT 200
                """, args, ITEM);
    }

    @Transactional
    public void resolveForBlog(String slug, Long reportId, AuthenticatedUser principal, ResolveRequest request) {
        Blog blog = requireBlogManager(slug, principal);
        PendingReport report = lockPending(reportId);
        if (!blog.getId().equals(report.blogId())) {
            throw notFound();
        }
        if (principal.id().equals(report.authorId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CANNOT_HANDLE_OWN_REPORT", "나에 대한 신고는 처리할 수 없습니다.");
        }
        String result = request.result() == null ? "" : request.result();
        switch (result) {
            case "NO_ISSUE" -> {
            }
            case "WARNING", "SUSPENSION", "KICK" -> {
                if (report.authorId() == null) {
                    throw new ApiException(HttpStatus.CONFLICT, "NO_TARGET_MEMBER", "제재할 멤버가 없습니다.");
                }
                sanctionService.apply(blog, report.authorId(), principal,
                        new SanctionService.SanctionRequest(result, request.days(), request.reason()), report.id());
            }
            default -> throw new ValidationFailedException(List.of(new ErrorResponse.FieldError("result",
                    "문제 없음, 경고, 정지, 강제 퇴장 중에서 골라 주세요.")));
        }
        finish(report, result, principal.id());
    }

    // ---- 메인 관리자 처리 (ADM-04) ----

    @Transactional(readOnly = true)
    public AdminPage<ReportItem> listForAdmin(String status, PageParams params) {
        MapSqlParameterSource args = new MapSqlParameterSource("status", "RESOLVED".equals(status) ? "RESOLVED" : "PENDING")
                .addValue("limit", params.size()).addValue("offset", (long) params.zeroBasedPage() * params.size());
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM reports r WHERE r.blog_id IS NULL AND r.status = :status",
                args, Long.class);
        List<ReportItem> items = jdbc.query(SELECT + """
                WHERE r.blog_id IS NULL AND r.status = :status
                ORDER BY r.created_at ASC LIMIT :limit OFFSET :offset
                """, args, ITEM);
        return AdminPage.of(items, params.page(), params.size(), total == null ? 0 : total);
    }

    @Transactional
    public void resolveForAdmin(Long reportId, AuthenticatedUser admin, ResolveRequest request,
            HttpServletRequest httpRequest) {
        PendingReport report = lockPending(reportId);
        if (report.blogId() != null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BLOG_REPORT", "블로그장이 처리할 신고입니다.");
        }
        String result = request.result() == null ? "" : request.result();
        String reason = request.reason() == null || request.reason().isBlank() ? "신고 처리" : request.reason().strip();
        switch (result) {
            case "NO_ISSUE" -> {
            }
            case "CONTENT_DELETED" -> {
                if ("POST".equals(report.targetType())) {
                    adminService.deletePost(report.targetId(), reason, admin, httpRequest);
                } else if ("COMMENT".equals(report.targetType())) {
                    adminService.deleteComment(report.targetId(), reason, admin, httpRequest);
                } else {
                    throw invalidResult();
                }
            }
            case "OWNER_WARNING", "OWNER_REVOKED", "BLOG_CLOSED" -> {
                if (report.snapshotBlogId() == null) {
                    throw invalidResult();
                }
                switch (result) {
                    case "OWNER_WARNING" -> ownerSanctionService.warnOwner(report.snapshotBlogId(), reason, report.id(),
                            admin, httpRequest);
                    case "OWNER_REVOKED" -> ownerSanctionService.revokeOwner(report.snapshotBlogId(), reason,
                            report.id(), admin, httpRequest);
                    default -> ownerSanctionService.forceClose(report.snapshotBlogId(), reason, admin, httpRequest);
                }
            }
            default -> throw invalidResult();
        }
        actionLogger.log(admin, "REPORT_RESOLVE", "REPORT", report.id(), result + ": " + reason, httpRequest);
        finish(report, result, admin.id());
    }

    // ---- 공통 ----

    private record PendingReport(Long id, Long blogId, String targetType, Long targetId, Long authorId,
            Long snapshotBlogId) {
    }

    private PendingReport lockPending(Long reportId) {
        List<PendingReport> rows = jdbc.query("""
                SELECT id, blog_id, target_type, target_id,
                       CAST(JSON_EXTRACT(target_snapshot, '$.authorId') AS UNSIGNED),
                       CAST(JSON_EXTRACT(target_snapshot, '$.blogId') AS UNSIGNED)
                FROM reports WHERE id = :id AND status = 'PENDING' FOR UPDATE
                """, new MapSqlParameterSource("id", reportId),
                (rs, i) -> new PendingReport(rs.getLong(1), nullableLong(rs, 2), rs.getString(3), rs.getLong(4),
                        nullableLong(rs, 5), nullableLong(rs, 6)));
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "처리할 신고를 찾을 수 없습니다.");
        }
        return rows.get(0);
    }

    /** 같은 접수처의 같은 대상 대기 신고를 모두 처리하고 신고자마다 알린다. */
    private void finish(PendingReport report, String result, Long processedBy) {
        MapSqlParameterSource args = new MapSqlParameterSource().addValue("type", report.targetType())
                .addValue("targetId", report.targetId()).addValue("blogId", report.blogId())
                .addValue("result", result).addValue("by", processedBy).addValue("now", LocalDateTime.now());
        String sameQueue = report.blogId() == null ? "blog_id IS NULL" : "blog_id = :blogId";
        List<Long[]> reporters = jdbc.query("SELECT id, reporter_id FROM reports WHERE status = 'PENDING' AND "
                + sameQueue + " AND target_type = :type AND target_id = :targetId", args,
                (rs, i) -> new Long[] {rs.getLong(1), rs.getLong(2)});
        jdbc.update("UPDATE reports SET status = 'RESOLVED', result = :result, processed_by_user_id = :by, "
                + "processed_at = :now WHERE status = 'PENDING' AND " + sameQueue
                + " AND target_type = :type AND target_id = :targetId", args);
        for (Long[] row : reporters) {
            notifications.send(row[1], NotificationType.REPORT_RESULT, "신고하신 내용을 확인하고 처리했어요.", null,
                    "REPORT_RESULT:" + row[0], notifications.extraFor(row[0],
                            "처리 결과: " + RESULT_LABELS.getOrDefault(result, result), null, null));
        }
    }

    private static final String SELECT = """
            SELECT r.id, r.target_type, r.target_id,
                   CAST(JSON_EXTRACT(r.target_snapshot, '$.authorId') AS UNSIGNED) AS author_id,
                   JSON_UNQUOTE(JSON_EXTRACT(r.target_snapshot, '$.author')) AS author,
                   CAST(r.target_snapshot AS CHAR) AS snapshot, r.reason_code, r.reason_detail, u.nickname,
                   r.status, r.result, r.created_at
            FROM reports r JOIN users u ON u.id = r.reporter_id
            """;

    private static final RowMapper<ReportItem> ITEM = (rs, i) -> {
        String code = rs.getString(7);
        String label;
        try {
            label = ReportReason.valueOf(code).label();
        } catch (IllegalArgumentException e) {
            label = code;
        }
        return new ReportItem(rs.getLong(1), rs.getString(2), rs.getLong(3), nullableLong(rs, 4),
                rs.getString(5), rs.getString(6), code, label, rs.getString(8), rs.getString(9), rs.getString(10),
                rs.getString(11), Times.toOffset(rs.getTimestamp(12).toLocalDateTime()));
    };

    /** NULL이면 null (BIGINT UNSIGNED도 long으로 읽는다). */
    private static Long nullableLong(java.sql.ResultSet rs, int column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Blog requireBlogManager(String slug, AuthenticatedUser principal) {
        Blog blog = accessService.check(slug, null, principal.id()).blog();
        BlogMember me = accessService.activeMembership(blog.getId(), principal.id());
        if (me == null || !me.canManageMembers()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "신고는 멤버 관리 권한이 있어야 처리할 수 있습니다.");
        }
        return blog;
    }

    private User member(String nickname) {
        String value = nickname == null ? "" : nickname.strip();
        return userRepository.findByNickname(value).filter(u -> u.isActive() && u.getRole() == UserRole.USER)
                .orElseThrow(ReportService::notFound);
    }

    private String nickname(Long userId) {
        return userRepository.findById(userId).map(User::getNickname).orElse(null);
    }

    private static ReportReason parseReason(String raw) {
        try {
            return ReportReason.valueOf(raw == null ? "" : raw);
        } catch (IllegalArgumentException e) {
            throw new ValidationFailedException(List.of(new ErrorResponse.FieldError("reason", "신고 사유를 골라 주세요.")));
        }
    }

    private static Long nonNull(Long id) {
        if (id == null) {
            throw notFound();
        }
        return id;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TARGET_NOT_FOUND", "신고할 대상을 찾을 수 없습니다.");
    }

    private static ValidationFailedException invalidResult() {
        return new ValidationFailedException(List.of(new ErrorResponse.FieldError("result", "이 신고에 쓸 수 없는 처리 결과입니다.")));
    }
}
