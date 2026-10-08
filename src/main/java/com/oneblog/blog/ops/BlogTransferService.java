package com.oneblog.blog.ops;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogAccessService;
import com.oneblog.blog.BlogCreateService;
import com.oneblog.blog.BlogMember;
import com.oneblog.blog.BlogMemberRepository;
import com.oneblog.blog.BlogRepository;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.ApiException;
import com.oneblog.common.web.Times;
import com.oneblog.member.User;
import com.oneblog.member.UserRepository;
import com.oneblog.notification.NotificationService;
import com.oneblog.notification.NotificationType;

/**
 * 블로그장 위임 (BLG-08).
 * - 블로그장이 멤버 한 명에게 요청한다. 대기 중인 요청은 블로그마다 하나 (새로 요청하면 예전 것은 취소)
 * - 폐쇄 예정 중에는 요청할 수 없다. 폐쇄를 누르면 대기 중인 요청은 취소된다 (BlogCloseService)
 * - 받은 멤버가 수락하면 블로그장이 되고, 예전 블로그장은 일반 멤버가 된다
 * - 7일 안에 응답이 없으면 04:00 배치가 자동 취소하고 블로그장에게 알린다
 */
@Service
public class BlogTransferService {

    public static final Duration EXPIRES_AFTER = Duration.ofDays(7);

    private final BlogAccessService accessService;
    private final BlogRepository blogRepository;
    private final BlogMemberRepository memberRepository;
    private final BlogTransferRequestRepository transferRepository;
    private final BlogManageService manageService;
    private final BlogCreateService createService;
    private final UserRepository userRepository;
    private final NotificationService notifications;

    public BlogTransferService(BlogAccessService accessService, BlogRepository blogRepository,
            BlogMemberRepository memberRepository, BlogTransferRequestRepository transferRepository,
            BlogManageService manageService, BlogCreateService createService, UserRepository userRepository,
            NotificationService notifications) {
        this.accessService = accessService;
        this.blogRepository = blogRepository;
        this.memberRepository = memberRepository;
        this.transferRepository = transferRepository;
        this.manageService = manageService;
        this.createService = createService;
        this.userRepository = userRepository;
        this.notifications = notifications;
    }

    public record TransferItem(Long id, String blogSlug, String blogName, String fromNickname, String toNickname,
            OffsetDateTime expiresAt) {
    }

    @Transactional
    public TransferItem request(String slug, Long toUserId, AuthenticatedUser principal) {
        Blog blog = accessService.check(slug, null, principal.id()).blog();
        manageService.requireOwner(blog, principal);
        if (blog.isClosing()) {
            throw new ApiException(HttpStatus.CONFLICT, "BLOG_CLOSING", "폐쇄 예정인 블로그는 폐쇄를 철회한 뒤에 위임할 수 있습니다.");
        }
        if (toUserId == null || toUserId.equals(principal.id())
                || memberRepository.findActive(blog.getId(), toUserId).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER_TARGET", "이 블로그의 다른 멤버를 골라 주세요.");
        }
        LocalDateTime now = LocalDateTime.now();
        transferRepository.findByBlogIdAndStatus(blog.getId(), TransferStatus.PENDING)
                .forEach(r -> r.finish(TransferStatus.CANCELED, now));
        BlogTransferRequest saved = transferRepository.saveAndFlush(
                BlogTransferRequest.pending(blog.getId(), principal.id(), toUserId, now.plus(EXPIRES_AFTER)));
        notifications.send(toUserId, NotificationType.TRANSFER_REQUEST,
                "블로그 「" + blog.getName() + "」의 블로그장 위임 요청을 받았어요. 7일 안에 수락하거나 거절해 주세요.",
                "/my-blogs.html", "TRANSFER_REQUEST:" + saved.getId());
        return toItem(saved, blog);
    }

    /** 블로그장이 대기 중인 요청을 취소한다. */
    @Transactional
    public void cancel(String slug, AuthenticatedUser principal) {
        Blog blog = accessService.check(slug, null, principal.id()).blog();
        manageService.requireOwner(blog, principal);
        LocalDateTime now = LocalDateTime.now();
        transferRepository.findByBlogIdAndStatus(blog.getId(), TransferStatus.PENDING)
                .forEach(r -> r.finish(TransferStatus.CANCELED, now));
    }

    @Transactional(readOnly = true)
    public TransferItem pending(String slug, AuthenticatedUser principal) {
        Blog blog = accessService.check(slug, null, principal.id()).blog();
        manageService.requireOwner(blog, principal);
        return transferRepository.findPending(blog.getId()).map(r -> toItem(r, blog)).orElse(null);
    }

    /** 나에게 온 대기 중인 위임 요청 (내 블로그 화면). */
    @Transactional(readOnly = true)
    public List<TransferItem> incoming(AuthenticatedUser principal) {
        List<TransferItem> items = new ArrayList<>();
        for (BlogTransferRequest r : transferRepository.findByToUserIdAndStatusOrderByCreatedAtDesc(principal.id(),
                TransferStatus.PENDING)) {
            Blog blog = blogRepository.findById(r.getBlogId()).filter(Blog::isOpen).orElse(null);
            if (blog != null) {
                items.add(toItem(r, blog));
            }
        }
        return items;
    }

    /** noRollbackFor: 받을 수 없게 된 요청은 오류를 주면서도 취소로 남겨야 한다. */
    @Transactional(noRollbackFor = ApiException.class)
    public void respond(Long requestId, boolean accept, AuthenticatedUser principal) {
        // 받는 회원을 먼저 잠가 같은 회원의 동시 수락(개수 제한)을 줄 세운다
        userRepository.findForUpdateById(principal.id());
        BlogTransferRequest request = transferRepository.findForUpdateById(requestId)
                .filter(r -> r.getToUserId().equals(principal.id()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND", "위임 요청을 찾을 수 없습니다."));
        if (!request.isPending()) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSFER_ALREADY_PROCESSED", "이미 처리된 요청입니다.");
        }
        Blog blog = blogRepository.findById(request.getBlogId()).filter(Blog::isOpen)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BLOG_NOT_FOUND", "블로그를 찾을 수 없습니다."));
        LocalDateTime now = LocalDateTime.now();
        if (!accept) {
            request.finish(TransferStatus.REJECTED, now);
            notifyOwner(request, blog, nickname(principal.id()) + "님이 블로그 「" + blog.getName() + "」 위임을 거절했어요.");
            return;
        }
        BlogMember from = memberRepository.findActive(blog.getId(), request.getFromUserId()).orElse(null);
        BlogMember to = memberRepository.findActive(blog.getId(), principal.id()).orElse(null);
        if (from == null || !from.isOwner() || to == null || blog.isClosing()) {
            request.finish(TransferStatus.CANCELED, now);
            throw new ApiException(HttpStatus.CONFLICT, "TRANSFER_NOT_POSSIBLE", "지금은 위임을 받을 수 없는 블로그입니다.");
        }
        createService.checkLimit(principal.id(), blog.getVisibility());
        from.makeMember();
        to.makeOwner();
        memberRepository.saveAndFlush(from);
        memberRepository.saveAndFlush(to);
        request.finish(TransferStatus.ACCEPTED, now);
        notifyOwner(request, blog, nickname(principal.id()) + "님이 블로그 「" + blog.getName() + "」 위임을 수락했어요. 이제 일반 멤버예요.");
    }

    /** 04:00 배치: 7일 지난 요청 자동 취소 + 블로그장 알림 (BLG-08). */
    @Transactional
    public int expire(LocalDateTime now) {
        List<BlogTransferRequest> expired = transferRepository.findExpired(now);
        for (BlogTransferRequest request : expired) {
            request.finish(TransferStatus.EXPIRED, now);
            blogRepository.findById(request.getBlogId()).ifPresent(blog -> notifyOwner(request, blog,
                    "블로그 「" + blog.getName() + "」 위임 요청이 7일 동안 응답이 없어 자동으로 취소됐어요."));
        }
        return expired.size();
    }

    private void notifyOwner(BlogTransferRequest request, Blog blog, String message) {
        notifications.send(request.getFromUserId(), NotificationType.TRANSFER_RESULT, message,
                blog.getSlug() == null ? null : "/blog/" + blog.getSlug(), "TRANSFER_RESULT:" + request.getId());
    }

    private TransferItem toItem(BlogTransferRequest r, Blog blog) {
        if (blog == null) {
            return null;
        }
        return new TransferItem(r.getId(), blog.getSlug(), blog.getName(), nickname(r.getFromUserId()),
                nickname(r.getToUserId()), Times.toOffset(r.getExpiresAt()));
    }

    private String nickname(Long userId) {
        return userRepository.findById(userId).map(User::getNickname).orElse("");
    }
}
