package com.oneblog.blog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.blog.subscription.BlogSubscriptionRepository;
import com.oneblog.common.web.ApiException;

/**
 * 블로그를 볼 수 있는지 한 곳에서 판단한다 (BLG-01, SEC-07, research R7).
 * 역할은 화면이나 토큰이 아니라 요청마다 저장된 멤버십으로 확인한다 (constitution III).
 * 관리자 숨김(007), 정지(013)도 여기서 판단한다. 블랙리스트는 참여 신청에서 막는다(BlacklistService).
 * 관리자는 숨김·비공개·일부 공개 블로그도 읽을 수 있고, 그때마다 활동 기록에 남는다 (ADM-02, ADM-06, D-106).
 */
@Service
public class BlogAccessService {

    private final BlogRepository blogRepository;
    private final BlogMemberRepository memberRepository;
    private final BlogPolicy policy;
    private final BlogSubscriptionRepository subscriptionRepository;
    private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc;
    private final com.oneblog.admin.AdminActionLogger adminActionLogger;

    public BlogAccessService(BlogRepository blogRepository, BlogMemberRepository memberRepository,
            BlogPolicy policy, BlogSubscriptionRepository subscriptionRepository,
            org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc,
            com.oneblog.admin.AdminActionLogger adminActionLogger) {
        this.adminActionLogger = adminActionLogger;
        this.blogRepository = blogRepository;
        this.memberRepository = memberRepository;
        this.policy = policy;
        this.subscriptionRepository = subscriptionRepository;
        this.jdbc = jdbc;
    }

    /** 볼 수 있으면 블로그와 내 역할(멤버가 아니면 null), 없으면 404, 볼 수 없으면 403. 403·404에는 블로그 정보를 싣지 않는다. */
    @Transactional(readOnly = true)
    public Access check(String rawSlug, String key, Long viewerId) {
        String slug = policy.normalizeSlug(rawSlug);
        Blog blog = (slug == null || !policy.isValidSlugFormat(slug)) ? null
                : blogRepository.findBySlug(slug).orElse(null);
        return check(blog, key, viewerId);
    }

    /** 이미 찾은 블로그로 판단한다 (글 상세처럼 블로그 주소 대신 글 번호로 들어올 때). */
    @Transactional(readOnly = true)
    public Access check(Blog blog, String key, Long viewerId) {
        if (blog == null || !blog.isOpen()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BLOG_NOT_FOUND", "블로그를 찾을 수 없습니다.");
        }

        BlogMember membership = viewerId == null ? null : memberRepository.findActive(blog.getId(), viewerId).orElse(null);
        if (membership != null) {
            // 정지된 멤버는 정지 기간 동안 이 블로그에 들어갈 수 없고, 기간과 사유를 안내받는다 (BLG-13, D-29)
            if (membership.isSuspended(java.time.LocalDateTime.now())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "MEMBER_SUSPENDED", suspensionMessage(blog, membership));
            }
            return new Access(blog, membership.getRole());
        }
        if (blog.isHidden()) {
            // 관리자가 숨긴 블로그는 멤버가 아니면 없는 블로그처럼 보인다 (ADM-02). 관리자는 볼 수 있다 (D-106)
            if (isAdmin(viewerId)) {
                return adminView(blog, viewerId, "숨긴 블로그");
            }
            throw new ApiException(HttpStatus.NOT_FOUND, "BLOG_NOT_FOUND", "블로그를 찾을 수 없습니다.");
        }

        return switch (blog.getVisibility()) {
            case PUBLIC -> new Access(blog, null);
            case UNLISTED -> {
                // 링크로 들어와 구독한 회원은 그 뒤로 링크 없이 들어온다 (D-50)
                if (matches(blog.getShareToken(), key)
                        || (viewerId != null && subscriptionRepository.existsByBlogIdAndUserId(blog.getId(), viewerId))) {
                    yield new Access(blog, null);
                }
                if (isAdmin(viewerId)) {
                    yield adminView(blog, viewerId, "일부 공개 블로그");
                }
                throw new ApiException(HttpStatus.FORBIDDEN, "LINK_REQUIRED", "링크가 있어야 볼 수 있는 블로그입니다.");
            }
            case PRIVATE -> {
                if (isAdmin(viewerId)) {
                    yield adminView(blog, viewerId, "비공개 블로그");
                }
                throw new ApiException(HttpStatus.FORBIDDEN, "PRIVATE_BLOG", "비공개 블로그입니다.");
            }
        };
    }

    private String suspensionMessage(Blog blog, BlogMember membership) {
        java.time.LocalDateTime until = membership.getSuspendedUntil();
        String when = until.getYear() >= 9999 ? "영구히"
                : until.format(java.time.format.DateTimeFormatter.ofPattern("yyyy년 M월 d일 H시")) + "까지";
        java.util.List<String> reasons = jdbc.queryForList("""
                SELECT reason FROM sanctions WHERE blog_id = :blogId AND user_id = :userId AND type = 'SUSPENSION'
                ORDER BY id DESC LIMIT 1
                """, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("blogId", blog.getId()).addValue("userId", membership.getUserId()), String.class);
        return "이 블로그에서 " + when + " 정지되었습니다." + (reasons.isEmpty() ? "" : " 사유: " + reasons.get(0));
    }

    /** 관리자 계정인지 (볼 수 없는 경우에만 묻는다). 역할은 요청마다 DB로 확인한다 (constitution III). */
    public boolean isAdmin(Long viewerId) {
        if (viewerId == null) {
            return false;
        }
        java.util.List<String> roles = jdbc.queryForList("SELECT role FROM users WHERE id = :id AND status = 'ACTIVE'",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("id", viewerId), String.class);
        return !roles.isEmpty() && "ADMIN".equals(roles.get(0));
    }

    private Access adminView(Blog blog, Long adminId, String what) {
        adminActionLogger.logView(adminId, "BLOG", blog.getId(), what + " 열람: " + blog.getSlug());
        return new Access(blog, null, true);
    }

    /** 길이와 내용을 함께, 시간 차이 없이 비교한다 (research R6). */
    private static boolean matches(String expected, String given) {
        if (expected == null || given == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }

    /** 지금 활성 멤버십 (없으면 null). 글쓰기·관리 권한 확인에 쓴다. */
    @Transactional(readOnly = true)
    public BlogMember activeMembership(Long blogId, Long userId) {
        if (userId == null) {
            return null;
        }
        return memberRepository.findActive(blogId, userId).orElse(null);
    }

    /** adminView: 관리자라서 볼 수 있는 경우(숨김·비공개·일부 공개) (D-106). */
    public record Access(Blog blog, BlogRole myRole, boolean adminView) {

        public Access(Blog blog, BlogRole myRole) {
            this(blog, myRole, false);
        }

        public boolean isMember() {
            return myRole != null;
        }
    }
}
