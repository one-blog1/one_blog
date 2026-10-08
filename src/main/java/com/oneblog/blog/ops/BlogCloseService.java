package com.oneblog.blog.ops;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogAccessService;
import com.oneblog.blog.BlogRepository;
import com.oneblog.blog.BlogStatus;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.ApiException;
import com.oneblog.notification.NotificationType;

/**
 * 블로그 폐쇄·철회 (BLG-09, D-12, D-14, D-67).
 * - 폐쇄 버튼: 누른 날짜(한국 시간) + 7일 04:00에 폐쇄. 대기 중인 위임 요청은 취소. 멤버(블로그장 제외)·구독자에게 바로 알림
 * - 폐쇄 예정 7일 동안은 평소처럼 운영하고, 블로그장이 철회할 수 있다 (같은 대상에게 철회 알림)
 * - 04:00 배치: 3일 전·1일 전 알림, 예정 시각이 지난 블로그 폐쇄 (밀린 날도 다음 배치가 처리)
 */
@Service
public class BlogCloseService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("M월 d일 H시");

    private final BlogAccessService accessService;
    private final BlogRepository blogRepository;
    private final BlogManageService manageService;
    private final BlogTransferRequestRepository transferRepository;
    private final BlogAudience audience;

    public BlogCloseService(BlogAccessService accessService, BlogRepository blogRepository,
            BlogManageService manageService, BlogTransferRequestRepository transferRepository, BlogAudience audience) {
        this.accessService = accessService;
        this.blogRepository = blogRepository;
        this.manageService = manageService;
        this.transferRepository = transferRepository;
        this.audience = audience;
    }

    @Transactional
    public LocalDateTime close(String slug, AuthenticatedUser principal) {
        Blog blog = accessService.check(slug, null, principal.id()).blog();
        manageService.requireOwner(blog, principal);
        if (blog.isClosing()) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CLOSING", "이미 폐쇄 예정인 블로그입니다.");
        }
        schedule(blog, "OWNER", LocalDateTime.now());
        return blog.getCloseScheduledAt();
    }

    /** 폐쇄 예약과 즉시 알림. 관리자 강제 폐쇄·블로그장 권한 박탈(013)도 쓴다. */
    @Transactional
    public void schedule(Blog blog, String reason, LocalDateTime now) {
        LocalDateTime at = BlogClock.closeAt(now);
        blog.scheduleClose(at, reason);
        blogRepository.saveAndFlush(blog);
        transferRepository.findByBlogIdAndStatus(blog.getId(), TransferStatus.PENDING)
                .forEach(r -> r.finish(TransferStatus.CANCELED, now));
        String why = switch (reason) {
            case "ADMIN" -> " 운영 정책에 따른 폐쇄 조치예요.";
            case "OWNER_REVOKED" -> " 블로그장 권한 박탈로 인한 폐쇄 조치예요.";
            default -> "";
        };
        audience.notifyAll(blog, NotificationType.BLOG_CLOSING,
                "블로그 「" + blog.getName() + "」이 " + at.format(WHEN) + "에 폐쇄될 예정이에요." + why
                        + " 필요한 글은 그 전에 챙겨 주세요.",
                dedupe(blog, "NOW@" + now));
    }

    @Transactional
    public void cancel(String slug, AuthenticatedUser principal) {
        Blog blog = accessService.check(slug, null, principal.id()).blog();
        manageService.requireOwner(blog, principal);
        if (!blog.isClosing()) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_CLOSING", "폐쇄 예정인 블로그가 아닙니다.");
        }
        if (!"OWNER".equals(blog.getCloseReason())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CLOSE_BY_ADMIN", "운영 조치로 인한 폐쇄는 철회할 수 없습니다.");
        }
        String key = dedupe(blog, "CANCEL@" + LocalDateTime.now());
        blog.cancelClose();
        blogRepository.saveAndFlush(blog);
        audience.notifyAll(blog, NotificationType.BLOG_CLOSE_CANCELED,
                "블로그 「" + blog.getName() + "」 폐쇄가 철회됐어요. 평소처럼 이용할 수 있어요.", key);
    }

    /** 04:00 배치: 폐쇄 3일 전·1일 전 알림 (한국 날짜 기준). */
    @Transactional
    public int remind(LocalDateTime now) {
        LocalDate today = BlogClock.seoulDate(now);
        int sent = 0;
        for (Blog blog : blogRepository.findByStatus(BlogStatus.CLOSING)) {
            if (blog.getCloseScheduledAt() == null) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(today, BlogClock.seoulDate(blog.getCloseScheduledAt()));
            if (days == 3 || days == 1) {
                audience.notifyAll(blog, NotificationType.BLOG_CLOSING,
                        "블로그 「" + blog.getName() + "」 폐쇄까지 " + days + "일 남았어요 ("
                                + blog.getCloseScheduledAt().format(WHEN) + ").",
                        dedupe(blog, "D" + days));
                sent++;
            }
        }
        return sent;
    }

    /** 04:00 배치: 예정 시각이 지난 블로그를 폐쇄한다. */
    @Transactional
    public int closeDue(LocalDateTime now) {
        List<Blog> due = blogRepository.findCloseDue(BlogStatus.CLOSING, now);
        for (Blog blog : due) {
            blog.close(now);
            transferRepository.findByBlogIdAndStatus(blog.getId(), TransferStatus.PENDING)
                    .forEach(r -> r.finish(TransferStatus.CANCELED, now));
        }
        blogRepository.saveAllAndFlush(due);
        return due.size();
    }

    /**
     * 알림 중복 방지 키. 즉시·철회 알림은 누른 시각을 넣어 폐쇄→철회→다시 폐쇄해도 새로 가고,
     * 3일·1일 전 알림은 예정 시각마다 한 번만 간다(배치를 다시 돌려도).
     */
    private static String dedupe(Blog blog, String step) {
        LocalDateTime at = blog.getCloseScheduledAt();
        return "BLOG_CLOSING:" + blog.getId() + ":" + (at == null ? "" : at.toString()) + ":" + step;
    }
}
