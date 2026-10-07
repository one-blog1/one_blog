package com.oneblog.sanction;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogAccessService;
import com.oneblog.blog.BlogMember;
import com.oneblog.blog.BlogPolicy;
import com.oneblog.blog.BlogRepository;
import com.oneblog.blog.join.JoinGate;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.ApiException;
import com.oneblog.common.web.ErrorResponse;
import com.oneblog.common.web.Times;
import com.oneblog.member.User;
import com.oneblog.member.UserRepository;
import com.oneblog.member.ValidationFailedException;
import com.oneblog.notification.NotificationService;
import com.oneblog.notification.NotificationType;

/**
 * 블로그 블랙리스트와 해제 문의 (BLG-11, BLG-12, D-34, D-55).
 * - 강제 퇴장하면 이름·이메일·전화번호 해시를 올린다. 이메일이나 전화번호가 맞으면 그 블로그에 참여 신청을 할 수 없다
 * - 기록은 고치거나 지울 수 없고 해제만 한다. 블로그장이 바뀌어도 새 블로그장·멤버 관리 부블로그장이 열람한다
 * - 걸린 사람은 "문의하기"를 남기고, 서버가 비교한 이름·전화번호 일치 여부를 보고 관리자가 해제·거절한다. 결과는 알림
 */
@Service
public class BlacklistService implements JoinGate {

    private final NamedParameterJdbcTemplate jdbc;
    private final PrivacyHasher hasher;
    private final UserRepository userRepository;
    private final BlogRepository blogRepository;
    private final BlogAccessService accessService;
    private final BlogPolicy blogPolicy;
    private final NotificationService notifications;

    public BlacklistService(NamedParameterJdbcTemplate jdbc, PrivacyHasher hasher, UserRepository userRepository,
            BlogRepository blogRepository, BlogAccessService accessService, BlogPolicy blogPolicy,
            NotificationService notifications) {
        this.jdbc = jdbc;
        this.hasher = hasher;
        this.userRepository = userRepository;
        this.blogRepository = blogRepository;
        this.accessService = accessService;
        this.blogPolicy = blogPolicy;
        this.notifications = notifications;
    }

    public record BlacklistItem(Long id, String reason, String createdBy, OffsetDateTime createdAt,
            OffsetDateTime releasedAt) {
    }

    public record InquiryItem(Long id, String nickname, String message, boolean nameMatched, boolean phoneMatched,
            String status, OffsetDateTime createdAt) {
    }

    /** 강제 퇴장 때 부른다 (SanctionService). */
    @Transactional
    public void add(Long blogId, User user, String reason, Long createdBy) {
        jdbc.update("""
                INSERT INTO blog_blacklists (blog_id, name_hash, email_hash, phone_hash, reason, created_by_user_id)
                VALUES (:blogId, :name, :email, :phone, :reason, :by)
                """, new MapSqlParameterSource()
                .addValue("blogId", blogId)
                .addValue("name", hasher.name(user.getName()))
                .addValue("email", hasher.email(user.getEmail()))
                .addValue("phone", hasher.phone(user.getPhone()))
                .addValue("reason", reason)
                .addValue("by", createdBy));
    }

    /** 해제되지 않은 기록 중 이메일이나 전화번호가 맞는 것 (없으면 null). */
    Long matching(Long blogId, User user) {
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM blog_blacklists
                WHERE blog_id = :blogId AND released_at IS NULL AND (email_hash = :email OR phone_hash = :phone)
                ORDER BY id DESC LIMIT 1
                """, new MapSqlParameterSource().addValue("blogId", blogId)
                .addValue("email", hasher.email(user.getEmail())).addValue("phone", hasher.phone(user.getPhone())),
                Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** 참여 신청 검사 (JoinGate). */
    @Override
    public Decision check(Blog blog, Long applicantId) {
        User user = userRepository.findById(applicantId).orElse(null);
        if (user != null && matching(blog.getId(), user) != null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BLACKLISTED",
                    "이 블로그에는 참여할 수 없습니다. 잘못 막혔다면 블랙리스트 해제를 문의해 주세요.");
        }
        return Decision.ALLOW;
    }

    @Transactional(readOnly = true)
    public List<BlacklistItem> list(String slug, AuthenticatedUser principal) {
        Blog blog = requireManager(slug, principal);
        return jdbc.query("""
                SELECT b.id, b.reason, u.nickname, b.created_at, b.released_at
                FROM blog_blacklists b LEFT JOIN users u ON u.id = b.created_by_user_id
                WHERE b.blog_id = :blogId ORDER BY b.created_at DESC
                """, new MapSqlParameterSource("blogId", blog.getId()),
                (rs, i) -> new BlacklistItem(rs.getLong(1), rs.getString(2), rs.getString(3),
                        Times.toOffset(rs.getTimestamp(4).toLocalDateTime()),
                        rs.getTimestamp(5) == null ? null : Times.toOffset(rs.getTimestamp(5).toLocalDateTime())));
    }

    /** 블랙리스트에 걸린 회원이 해제를 문의한다 (BLG-12). 걸리지 않았으면 문의할 것이 없다. */
    @Transactional
    public void inquire(String rawSlug, AuthenticatedUser principal, String rawMessage) {
        String slug = blogPolicy.normalizeSlug(rawSlug);
        Blog blog = slug == null ? null : blogRepository.findBySlug(slug).filter(Blog::isOpen).orElse(null);
        if (blog == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BLOG_NOT_FOUND", "블로그를 찾을 수 없습니다.");
        }
        String message = rawMessage == null ? "" : rawMessage.strip();
        if (message.isEmpty() || message.codePointCount(0, message.length()) > 1000) {
            throw new ValidationFailedException(List.of(
                    new ErrorResponse.FieldError("message", "문의 내용을 1~1,000자로 적어 주세요.")));
        }
        User user = userRepository.findById(principal.id()).orElseThrow();
        Long blacklistId = matching(blog.getId(), user);
        if (blacklistId == null) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_BLACKLISTED", "이 블로그의 블랙리스트에 없습니다.");
        }
        Integer pending = jdbc.queryForObject("""
                SELECT COUNT(*) FROM blacklist_inquiries WHERE blog_id = :blogId AND user_id = :userId AND status = 'PENDING'
                """, new MapSqlParameterSource().addValue("blogId", blog.getId()).addValue("userId", user.getId()),
                Integer.class);
        if (pending != null && pending > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "INQUIRY_PENDING", "이미 문의한 내용이 처리를 기다리고 있습니다.");
        }
        jdbc.update("""
                INSERT INTO blacklist_inquiries (blacklist_id, user_id, blog_id, message, name_matched, phone_matched)
                SELECT b.id, :userId, b.blog_id, :message, b.name_hash = :name, b.phone_hash = :phone
                FROM blog_blacklists b WHERE b.id = :blacklistId
                """, new MapSqlParameterSource().addValue("userId", user.getId()).addValue("message", message)
                .addValue("name", hasher.name(user.getName())).addValue("phone", hasher.phone(user.getPhone()))
                .addValue("blacklistId", blacklistId));
    }

    @Transactional(readOnly = true)
    public List<InquiryItem> inquiries(String slug, AuthenticatedUser principal) {
        Blog blog = requireManager(slug, principal);
        return jdbc.query("""
                SELECT i.id, u.nickname, i.message, i.name_matched, i.phone_matched, i.status, i.created_at
                FROM blacklist_inquiries i JOIN users u ON u.id = i.user_id
                WHERE i.blog_id = :blogId ORDER BY i.status = 'PENDING' DESC, i.created_at DESC
                """, new MapSqlParameterSource("blogId", blog.getId()),
                (rs, i) -> new InquiryItem(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4),
                        rs.getBoolean(5), rs.getString(6), Times.toOffset(rs.getTimestamp(7).toLocalDateTime())));
    }

    /** 문의 처리: release면 블랙리스트를 해제한다. 결과는 문의한 회원에게 알린다 (3.6 BLACKLIST_RESULT). */
    @Transactional
    public void resolveInquiry(String slug, Long inquiryId, boolean release, AuthenticatedUser principal) {
        Blog blog = requireManager(slug, principal);
        MapSqlParameterSource args = new MapSqlParameterSource().addValue("id", inquiryId)
                .addValue("blogId", blog.getId()).addValue("now", LocalDateTime.now())
                .addValue("status", release ? "RELEASED" : "REJECTED");
        List<Long[]> rows = jdbc.query("""
                SELECT user_id, blacklist_id FROM blacklist_inquiries
                WHERE id = :id AND blog_id = :blogId AND status = 'PENDING' FOR UPDATE
                """, args, (rs, i) -> new Long[] {rs.getLong(1), rs.getObject(2) == null ? null : rs.getLong(2)});
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "INQUIRY_NOT_FOUND", "처리할 문의를 찾을 수 없습니다.");
        }
        jdbc.update("UPDATE blacklist_inquiries SET status = :status, processed_at = :now WHERE id = :id", args);
        if (release && rows.get(0)[1] != null) {
            jdbc.update("UPDATE blog_blacklists SET released_at = :now WHERE id = :blacklistId AND released_at IS NULL",
                    new MapSqlParameterSource().addValue("now", LocalDateTime.now())
                            .addValue("blacklistId", rows.get(0)[1]));
        }
        notifications.send(rows.get(0)[0], NotificationType.BLACKLIST_RESULT,
                "블로그 「" + blog.getName() + "」 블랙리스트 해제 문의가 " + (release ? "받아들여졌어요. 다시 참여를 신청할 수 있어요."
                        : "거절됐어요."),
                "/blog/" + blog.getSlug(), "BLACKLIST_RESULT:" + inquiryId);
    }

    private Blog requireManager(String slug, AuthenticatedUser principal) {
        Blog blog = accessService.check(slug, null, principal.id()).blog();
        BlogMember me = accessService.activeMembership(blog.getId(), principal.id());
        if (me == null || !me.canManageMembers()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "블랙리스트는 멤버 관리 권한이 있어야 볼 수 있습니다.");
        }
        return blog;
    }
}
