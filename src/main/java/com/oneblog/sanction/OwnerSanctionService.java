package com.oneblog.sanction;

import java.time.LocalDateTime;
import java.util.Comparator;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.admin.AdminActionLogger;
import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogMember;
import com.oneblog.blog.BlogMemberRepository;
import com.oneblog.blog.BlogRepository;
import com.oneblog.blog.BlogStatus;
import com.oneblog.blog.ops.BlogCloseService;
import com.oneblog.blog.ops.BlogTransferRequestRepository;
import com.oneblog.blog.ops.TransferStatus;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.ApiException;
import com.oneblog.notification.NotificationService;
import com.oneblog.notification.NotificationType;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 메인 관리자의 블로그장 제재와 강제 폐쇄 (ADM-02, ADM-07, 3.7, D-44, D-54).
 * - 블로그장 경고. 1년 안에 3번이면 권한을 박탈한다
 * - 권한 박탈(강퇴): 블로그장은 일반 멤버로 남고, 가장 먼저 부블로그장이 된 사람이 블로그장이 된다.
 *   부블로그장이 없으면 7일 뒤 04:00 폐쇄를 예약하고 "블로그장 권한 박탈로 인한 폐쇄 조치"로 알린다
 * - 강제 폐쇄: 7일 뒤 04:00 폐쇄 예약(블로그장이 철회할 수 없음). 멤버·구독자에게 BLG-09와 같은 알림
 * 모든 조치는 관리자 활동 기록에 남는다 (ADM-06).
 */
@Service
public class OwnerSanctionService {

    public static final int OWNER_WARNING_LIMIT = 3;

    private final BlogRepository blogRepository;
    private final BlogMemberRepository memberRepository;
    private final SanctionRepository sanctionRepository;
    private final BlogCloseService closeService;
    private final BlogTransferRequestRepository transferRepository;
    private final AdminActionLogger actionLogger;
    private final NotificationService notifications;

    public OwnerSanctionService(BlogRepository blogRepository, BlogMemberRepository memberRepository,
            SanctionRepository sanctionRepository, BlogCloseService closeService,
            BlogTransferRequestRepository transferRepository, AdminActionLogger actionLogger,
            NotificationService notifications) {
        this.blogRepository = blogRepository;
        this.memberRepository = memberRepository;
        this.sanctionRepository = sanctionRepository;
        this.closeService = closeService;
        this.transferRepository = transferRepository;
        this.actionLogger = actionLogger;
        this.notifications = notifications;
    }

    public record Result(String outcome, String newOwnerNickname) {
    }

    @Transactional
    public Result warnOwner(Long blogId, String rawReason, Long reportId, AuthenticatedUser admin,
            HttpServletRequest request) {
        Blog blog = openBlog(blogId);
        Long ownerId = owner(blog);
        String reason = SanctionService.reason(rawReason, true);
        LocalDateTime now = LocalDateTime.now();
        sanctionRepository.saveAndFlush(Sanction.of(blogId, ownerId, SanctionType.OWNER_WARNING, true, admin.id(), reason,
                reportId));
        actionLogger.log(admin, "OWNER_WARNING", "BLOG", blogId, reason, request);
        long warnings = sanctionRepository.countSince(blogId, ownerId, SanctionType.OWNER_WARNING, now.minusYears(1));
        notifications.send(ownerId, NotificationType.OWNER_SANCTION, "블로그 「" + blog.getName()
                + "」 운영에 대해 관리자 경고를 받았어요 (" + warnings + "/" + OWNER_WARNING_LIMIT + "). 사유: " + reason, null, null);
        if (warnings >= OWNER_WARNING_LIMIT) {
            return revoke(blog, ownerId, "경고 " + OWNER_WARNING_LIMIT + "회 누적", reportId, admin, request, now);
        }
        return new Result("WARNED", null);
    }

    @Transactional
    public Result revokeOwner(Long blogId, String rawReason, Long reportId, AuthenticatedUser admin,
            HttpServletRequest request) {
        Blog blog = openBlog(blogId);
        return revoke(blog, owner(blog), SanctionService.reason(rawReason, true), reportId, admin, request,
                LocalDateTime.now());
    }

    private Result revoke(Blog blog, Long ownerId, String reason, Long reportId, AuthenticatedUser admin,
            HttpServletRequest request, LocalDateTime now) {
        BlogMember owner = memberRepository.findActive(blog.getId(), ownerId).orElseThrow();
        sanctionRepository.save(Sanction.of(blog.getId(), ownerId, SanctionType.OWNER_REVOKE, true, admin.id(), reason,
                reportId));
        transferRepository.findByBlogIdAndStatus(blog.getId(), TransferStatus.PENDING)
                .forEach(r -> r.finish(TransferStatus.CANCELED, now));
        owner.makeMember();
        memberRepository.saveAndFlush(owner);
        actionLogger.log(admin, "OWNER_REVOKE", "BLOG", blog.getId(), reason, request);
        notifications.send(ownerId, NotificationType.OWNER_SANCTION, "블로그 「" + blog.getName()
                + "」의 블로그장 권한이 박탈됐어요. 이제 일반 멤버예요. 사유: " + reason, null, null);

        BlogMember successor = memberRepository.findSubOwners(blog.getId()).stream()
                .filter(m -> m.getSubOwnerSince() != null)
                .min(Comparator.comparing(BlogMember::getSubOwnerSince).thenComparing(BlogMember::getId))
                .orElse(null);
        if (successor != null) {
            successor.makeOwner();
            memberRepository.saveAndFlush(successor);
            notifications.send(successor.getUserId(), NotificationType.TRANSFER_RESULT, "블로그 「" + blog.getName()
                    + "」의 블로그장 권한이 박탈되어, 가장 먼저 부블로그장이 된 내가 블로그장이 됐어요.",
                    blog.getSlug() == null ? null : "/blog/" + blog.getSlug(), null);
            return new Result("TRANSFERRED", null);
        }
        if (!blog.isClosing()) {
            closeService.schedule(blog, "OWNER_REVOKED", now);
        }
        return new Result("CLOSING", null);
    }

    /** 강제 폐쇄 (ADM-02). 이미 폐쇄 예정이면 사유만 관리자 폐쇄로 바꾸지 않고 그대로 둔다. */
    @Transactional
    public void forceClose(Long blogId, String rawReason, AuthenticatedUser admin, HttpServletRequest request) {
        Blog blog = openBlog(blogId);
        String reason = SanctionService.reason(rawReason, true);
        if (blog.isClosing() && !"OWNER".equals(blog.getCloseReason())) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CLOSING", "이미 폐쇄 예정인 블로그입니다.");
        }
        closeService.schedule(blog, "ADMIN", LocalDateTime.now());
        actionLogger.log(admin, "BLOG_FORCE_CLOSE", "BLOG", blogId, reason, request);
        Long ownerId = memberRepository.findOwnerId(blogId);
        notifications.send(ownerId, NotificationType.OWNER_SANCTION, "블로그 「" + blog.getName()
                + "」이 운영 정책에 따라 7일 뒤 폐쇄돼요. 사유: " + reason, null, null);
    }

    private Blog openBlog(Long blogId) {
        return blogRepository.findById(blogId).filter(b -> b.isOpen() && b.getStatus() != BlogStatus.CLOSED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BLOG_NOT_FOUND", "블로그를 찾을 수 없습니다."));
    }

    private Long owner(Blog blog) {
        Long ownerId = memberRepository.findOwnerId(blog.getId());
        if (ownerId == null) {
            throw new ApiException(HttpStatus.CONFLICT, "NO_OWNER", "블로그장이 없는 블로그입니다.");
        }
        return ownerId;
    }
}
