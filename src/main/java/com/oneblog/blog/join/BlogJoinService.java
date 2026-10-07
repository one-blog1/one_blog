package com.oneblog.blog.join;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogAccessService;
import com.oneblog.blog.BlogJoinPolicy;
import com.oneblog.blog.BlogMember;
import com.oneblog.blog.BlogMemberRepository;
import com.oneblog.blog.BlogMemberStatus;
import com.oneblog.blog.BlogRepository;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.ApiException;
import com.oneblog.member.UserRepository;
import com.oneblog.member.UserRole;

/**
 * 블로그 참여 신청·승인 (BLG-04, BLG-05, 6.5).
 * 자유 참여는 바로 멤버가 되고, 승인제는 신청을 남겨 블로그장(멤버 관리 권한을 받은 부블로그장 포함)이 처리한다.
 * 역할은 요청마다 저장된 멤버십으로 확인한다 (SEC-07).
 */
@Service
public class BlogJoinService {

    /** 거절된 뒤 다시 신청할 수 있을 때까지 (6.5). */
    public static final Duration REAPPLY_AFTER_REJECT = Duration.ofDays(7);

    private final BlogAccessService accessService;
    private final BlogRepository blogRepository;
    private final BlogMemberRepository memberRepository;
    private final BlogJoinRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final List<JoinListener> listeners;
    private final List<JoinGate> gates;

    public BlogJoinService(BlogAccessService accessService, BlogRepository blogRepository,
            BlogMemberRepository memberRepository, BlogJoinRequestRepository requestRepository,
            UserRepository userRepository, List<JoinListener> listeners, List<JoinGate> gates) {
        this.accessService = accessService;
        this.blogRepository = blogRepository;
        this.memberRepository = memberRepository;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.listeners = listeners;
        this.gates = gates;
    }

    /** 내 참여 상태. 블로그를 볼 수 없으면 BlogAccessService가 403·404를 던진다. */
    @Transactional(readOnly = true)
    public JoinStatusResponse status(String slug, String key, AuthenticatedUser principal) {
        Blog blog = accessService.check(slug, key, principal.id()).blog();
        return currentStatus(blog.getId(), principal.id());
    }

    /**
     * 참여 신청 (BLG-04). 자유 참여면 바로 멤버(MEMBER), 승인제면 대기(PENDING).
     * 블로그를 볼 수 있는 사람만 신청한다: 비공개는 멤버만 보므로 신청할 수 없고, 일부 공개는 공유 링크가 필요하다.
     */
    @Transactional
    public JoinStatusResponse apply(String slug, String key, AuthenticatedUser principal) {
        rejectAdmin(principal);
        Blog blog = accessService.check(slug, key, principal.id()).blog();
        // 같은 회원의 신청이 동시에 두 번 들어와도 하나만 남게 회원 행을 잠근다
        userRepository.findForUpdateById(principal.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "로그인이 필요합니다."));

        Optional<BlogMember> membership = memberRepository.findByBlogIdAndUserId(blog.getId(), principal.id());
        if (membership.isPresent() && membership.get().isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER", "이미 이 블로그의 멤버입니다.");
        }
        // 블랙리스트(BLG-11)에 걸리면 막고, 블로그장이 차단한 회원은 자동 거절한다 (SOC-05, D-36)
        for (JoinGate gate : gates) {
            if (gate.check(blog, principal.id()) == JoinGate.Decision.AUTO_REJECT) {
                JoinStatusResponse current = currentStatus(blog.getId(), principal.id());
                if ("REJECTED".equals(current.status())) {
                    throw new ApiException(HttpStatus.CONFLICT, "REAPPLY_TOO_SOON", "거절된 신청은 7일 뒤에 다시 신청할 수 있습니다.");
                }
                BlogJoinRequest request = requestRepository.saveAndFlush(BlogJoinRequest.pending(blog.getId(), principal.id()));
                Long ownerId = memberRepository.findOwnerId(blog.getId());
                request.reject(ownerId == null ? principal.id() : ownerId);
                return currentStatus(blog.getId(), principal.id());
            }
        }
        if (membership.isPresent() && membership.get().getStatus() == BlogMemberStatus.KICKED && gates.isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "JOIN_BLOCKED", "이 블로그에는 참여할 수 없습니다.");
        }

        if (blog.getJoinPolicy() == BlogJoinPolicy.OPEN) {
            addMember(blog.getId(), principal.id(), membership);
            return JoinStatusResponse.of("MEMBER");
        }

        JoinStatusResponse current = currentStatus(blog.getId(), principal.id());
        if ("PENDING".equals(current.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "JOIN_REQUEST_PENDING", "이미 참여를 신청했습니다. 블로그장의 승인을 기다려 주세요.");
        }
        if ("REJECTED".equals(current.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "REAPPLY_TOO_SOON", "거절된 신청은 7일 뒤에 다시 신청할 수 있습니다.");
        }
        requestRepository.save(BlogJoinRequest.pending(blog.getId(), principal.id()));
        listeners.forEach(l -> l.requested(blog, principal.id()));
        return JoinStatusResponse.of("PENDING");
    }

    /** 대기 중인 내 신청 취소. */
    @Transactional
    public JoinStatusResponse cancel(String slug, String key, AuthenticatedUser principal) {
        Blog blog = accessService.check(slug, key, principal.id()).blog();
        BlogJoinRequest latest = requestRepository
                .findFirstByBlogIdAndUserIdOrderByCreatedAtDescIdDesc(blog.getId(), principal.id())
                .filter(BlogJoinRequest::isPending)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "JOIN_REQUEST_NOT_FOUND", "취소할 신청이 없습니다."));
        BlogJoinRequest locked = requestRepository.findForUpdateById(latest.getId()).orElseThrow();
        if (!locked.isPending()) {
            throw new ApiException(HttpStatus.CONFLICT, "JOIN_REQUEST_ALREADY_PROCESSED", "이미 처리된 신청입니다.");
        }
        locked.cancel();
        return JoinStatusResponse.of("NONE");
    }

    /** 대기 중인 신청 목록 (블로그장, 멤버 관리 권한을 받은 부블로그장). */
    @Transactional(readOnly = true)
    public List<JoinRequestItem> pendingRequests(String slug, AuthenticatedUser principal) {
        Blog blog = requireManager(slug, principal);
        return requestRepository.findPendingWithNickname(blog.getId()).stream()
                .map(row -> {
                    BlogJoinRequest r = (BlogJoinRequest) row[0];
                    return new JoinRequestItem(r.getId(), (String) row[1],
                            r.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime());
                })
                .toList();
    }

    /** 승인 (BLG-05). 신청자는 멤버가 되고 멤버 수가 늘어난다. */
    @Transactional
    public void approve(String slug, Long requestId, AuthenticatedUser principal) {
        Blog blog = requireManager(slug, principal);
        BlogJoinRequest request = lockPending(blog, requestId);
        request.approve(principal.id());
        addMember(blog.getId(), request.getUserId(),
                memberRepository.findByBlogIdAndUserId(blog.getId(), request.getUserId()));
        listeners.forEach(l -> l.processed(blog, request.getUserId(), true));
    }

    /** 거절 (BLG-05). 신청자는 7일 뒤 다시 신청할 수 있다 (6.5). */
    @Transactional
    public void reject(String slug, Long requestId, AuthenticatedUser principal) {
        Blog blog = requireManager(slug, principal);
        BlogJoinRequest request = lockPending(blog, requestId);
        request.reject(principal.id());
        listeners.forEach(l -> l.processed(blog, request.getUserId(), false));
    }

    private BlogJoinRequest lockPending(Blog blog, Long requestId) {
        BlogJoinRequest request = requestRepository.findForUpdateById(requestId)
                .filter(r -> r.getBlogId().equals(blog.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "JOIN_REQUEST_NOT_FOUND", "신청을 찾을 수 없습니다."));
        if (!request.isPending()) {
            throw new ApiException(HttpStatus.CONFLICT, "JOIN_REQUEST_ALREADY_PROCESSED", "이미 처리된 신청입니다.");
        }
        return request;
    }

    /** 멤버로 등록한다. 예전에 떠났던 행이 있으면 되살린다. */
    private void addMember(Long blogId, Long userId, Optional<BlogMember> existing) {
        if (existing.isPresent()) {
            BlogMember member = existing.get();
            if (member.isActive()) {
                return;
            }
            member.rejoin();
        } else {
            memberRepository.save(BlogMember.member(blogId, userId));
        }
        blogRepository.addMemberCount(blogId, 1);
    }

    private JoinStatusResponse currentStatus(Long blogId, Long userId) {
        if (memberRepository.findActive(blogId, userId).isPresent()) {
            return JoinStatusResponse.of("MEMBER");
        }
        Optional<BlogJoinRequest> latest = requestRepository
                .findFirstByBlogIdAndUserIdOrderByCreatedAtDescIdDesc(blogId, userId);
        if (latest.isEmpty()) {
            return JoinStatusResponse.of("NONE");
        }
        BlogJoinRequest request = latest.get();
        return switch (request.getStatus()) {
            case PENDING -> JoinStatusResponse.of("PENDING");
            case REJECTED -> {
                LocalDateTime retryAt = request.getProcessedAt().plus(REAPPLY_AFTER_REJECT);
                yield retryAt.isAfter(LocalDateTime.now())
                        ? new JoinStatusResponse("REJECTED", retryAt.atZone(ZoneId.systemDefault()).toOffsetDateTime())
                        : JoinStatusResponse.of("NONE");
            }
            case APPROVED, CANCELED -> JoinStatusResponse.of("NONE");
        };
    }

    /** 블로그장 또는 멤버 관리 권한을 받은 부블로그장인지 DB 멤버십으로 확인한다 (SEC-07). */
    private Blog requireManager(String slug, AuthenticatedUser principal) {
        Blog blog = accessService.check(slug, null, principal.id()).blog();
        boolean manager = memberRepository.findActive(blog.getId(), principal.id())
                .map(BlogMember::canManageMembers).orElse(false);
        if (!manager) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "이 블로그의 멤버를 관리할 권한이 없습니다.");
        }
        return blog;
    }

    private static void rejectAdmin(AuthenticatedUser principal) {
        if (principal.role() == UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_NOT_ALLOWED", "관리자 계정은 블로그 활동을 할 수 없습니다.");
        }
    }
}
