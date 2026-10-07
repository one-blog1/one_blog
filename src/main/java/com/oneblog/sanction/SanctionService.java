package com.oneblog.sanction;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogAccessService;
import com.oneblog.blog.BlogMember;
import com.oneblog.blog.BlogMemberRepository;
import com.oneblog.blog.BlogRepository;
import com.oneblog.blog.BlogRole;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.ApiException;
import com.oneblog.common.web.ErrorResponse;
import com.oneblog.member.User;
import com.oneblog.member.UserRepository;
import com.oneblog.member.ValidationFailedException;
import com.oneblog.notification.NotificationService;
import com.oneblog.notification.NotificationType;
import com.oneblog.post.PostRepository;

/**
 * 블로그장의 멤버 제재 (BLG-11, BLG-13, 3.7). 블로그장과 멤버 관리 권한을 받은 부블로그장이 한다.
 * - 경고, 정지(3·14·30일·영구, 그 블로그에만 못 들어감), 정지 해제, 강제 퇴장(블랙리스트 등록, 글은 "탈퇴한 계정")
 * - 블로그장은 제재할 수 없다(관리자가 ADM-07로). 부블로그장은 블로그장만 제재한다. 자기 자신은 제재할 수 없다
 * - 정지 3번(1년 안)이면 멤버 목록에 표시하고, 강제 퇴장은 블로그장이 직접 한다 (D-46)
 * 대상에게 알림이 간다 (3.6 SANCTION, 끌 수 없음).
 */
@Service
public class SanctionService {

    public static final Set<Integer> SUSPENSION_DAYS = Set.of(3, 14, 30);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("M월 d일 H시");

    private final BlogAccessService accessService;
    private final BlogRepository blogRepository;
    private final BlogMemberRepository memberRepository;
    private final SanctionRepository sanctionRepository;
    private final BlacklistService blacklistService;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final NotificationService notifications;

    public SanctionService(BlogAccessService accessService, BlogRepository blogRepository,
            BlogMemberRepository memberRepository, SanctionRepository sanctionRepository,
            BlacklistService blacklistService, UserRepository userRepository, PostRepository postRepository,
            NotificationService notifications) {
        this.accessService = accessService;
        this.blogRepository = blogRepository;
        this.memberRepository = memberRepository;
        this.sanctionRepository = sanctionRepository;
        this.blacklistService = blacklistService;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.notifications = notifications;
    }

    public record SanctionRequest(String type, Integer days, String reason) {
    }

    @Transactional
    public void apply(String slug, Long userId, AuthenticatedUser principal, SanctionRequest request) {
        Blog blog = accessService.check(slug, null, principal.id()).blog();
        apply(blog, userId, principal, request, null);
    }

    /** 신고 처리(ReportService)도 이것을 부른다. type: WARNING, SUSPENSION, KICK, RELEASE. */
    @Transactional
    public void apply(Blog blog, Long userId, AuthenticatedUser principal, SanctionRequest request, Long reportId) {
        BlogMember actor = accessService.activeMembership(blog.getId(), principal.id());
        if (actor == null || !actor.canManageMembers()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "멤버를 제재할 권한이 없습니다.");
        }
        BlogMember target = memberRepository.findActive(blog.getId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "멤버를 찾을 수 없습니다."));
        if (target.getUserId().equals(principal.id())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_SANCTION_SELF", "자기 자신은 제재할 수 없습니다.");
        }
        if (target.getRole() == BlogRole.OWNER
                || (target.getRole() == BlogRole.SUB_OWNER && actor.getRole() != BlogRole.OWNER)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CANNOT_SANCTION", "블로그장·부블로그장은 이 권한으로 제재할 수 없습니다.");
        }
        String type = request.type() == null ? "" : request.type();
        String reason = reason(request.reason(), !"RELEASE".equals(type));
        LocalDateTime now = LocalDateTime.now();
        switch (type) {
            case "WARNING" -> {
                sanctionRepository.save(Sanction.of(blog.getId(), userId, SanctionType.WARNING, false, principal.id(),
                        reason, reportId));
                notify(userId, blog, "블로그 「" + blog.getName() + "」에서 경고를 받았어요. 사유: " + reason);
            }
            case "SUSPENSION" -> {
                if (request.days() != null && !SUSPENSION_DAYS.contains(request.days())) {
                    throw new ValidationFailedException(List.of(
                            new ErrorResponse.FieldError("days", "정지 기간은 3일, 14일, 30일, 영구 중에서 골라 주세요.")));
                }
                Sanction sanction = Sanction.of(blog.getId(), userId, SanctionType.SUSPENSION, false, principal.id(),
                        reason, reportId).suspendFor(request.days(), now);
                sanctionRepository.save(sanction);
                target.suspendUntil(sanction.getEndsAt());
                memberRepository.saveAndFlush(target);
                notify(userId, blog, "블로그 「" + blog.getName() + "」에서 "
                        + (request.days() == null ? "영구히" : sanction.getEndsAt().format(WHEN) + "까지") + " 정지됐어요. 사유: "
                        + reason);
            }
            case "RELEASE" -> {
                sanctionRepository.findOpen(blog.getId(), userId, SanctionType.SUSPENSION).forEach(s -> s.release(now));
                target.releaseSuspension();
                memberRepository.saveAndFlush(target);
                notify(userId, blog, "블로그 「" + blog.getName() + "」 정지가 풀렸어요.");
            }
            case "KICK" -> kick(blog, target, principal.id(), reason, reportId, now);
            default -> throw new ValidationFailedException(List.of(
                    new ErrorResponse.FieldError("type", "경고, 정지, 정지 해제, 강제 퇴장 중에서 골라 주세요.")));
        }
    }

    /** 강제 퇴장: 블랙리스트 등록, 멤버십 KICKED, 글은 "탈퇴한 계정" (BLG-11, D-33). */
    private void kick(Blog blog, BlogMember target, Long actorId, String reason, Long reportId, LocalDateTime now) {
        User user = userRepository.findById(target.getUserId()).orElseThrow();
        sanctionRepository.save(Sanction.of(blog.getId(), target.getUserId(), SanctionType.KICK, false, actorId, reason,
                reportId));
        blacklistService.add(blog.getId(), user, reason, actorId);
        target.kick(now);
        memberRepository.saveAndFlush(target);
        blogRepository.addMemberCount(blog.getId(), -1);
        postRepository.detachAuthor(blog.getId(), target.getUserId());
        notify(target.getUserId(), blog, "블로그 「" + blog.getName() + "」에서 강제 퇴장됐어요. 사유: " + reason);
    }

    /** 1년 안에 받은 정지 수 (멤버 목록 표시, D-46). */
    @Transactional(readOnly = true)
    public long suspensionCount(Long blogId, Long userId) {
        return sanctionRepository.countSince(blogId, userId, SanctionType.SUSPENSION, LocalDateTime.now().minusYears(1));
    }

    private void notify(Long userId, Blog blog, String message) {
        notifications.send(userId, NotificationType.SANCTION, message, null, null);
    }

    static String reason(String raw, boolean required) {
        String reason = raw == null ? "" : raw.strip();
        if (required && reason.isEmpty()) {
            throw new ValidationFailedException(List.of(new ErrorResponse.FieldError("reason", "사유를 적어 주세요.")));
        }
        if (reason.codePointCount(0, reason.length()) > 500) {
            throw new ValidationFailedException(List.of(new ErrorResponse.FieldError("reason", "사유는 500자까지 쓸 수 있습니다.")));
        }
        return reason.isEmpty() ? "-" : reason;
    }
}
