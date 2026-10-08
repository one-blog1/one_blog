package com.oneblog.blog.ops;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.PageParams;
import com.oneblog.common.web.Times;

/** 블로그 운영 API: 정보 수정, 공개 범위, 멤버·부블로그장, 블로그 탈퇴, 위임, 폐쇄 (BLG-01, BLG-07~09, D-71). */
@RestController
public class BlogOpsController {

    private final BlogManageService manageService;
    private final BlogTransferService transferService;
    private final BlogCloseService closeService;
    private final BlogMemberStatsService statsService;

    public BlogOpsController(BlogManageService manageService, BlogTransferService transferService,
            BlogCloseService closeService, BlogMemberStatsService statsService) {
        this.statsService = statsService;
        this.manageService = manageService;
        this.transferService = transferService;
        this.closeService = closeService;
    }

    public record TransferRequest(Long toUserId) {
    }

    @PutMapping("/api/blogs/{slug}/info")
    public ResponseEntity<Void> updateInfo(@PathVariable("slug") String slug,
            @AuthenticationPrincipal AuthenticatedUser principal, @RequestBody BlogManageService.InfoRequest request) {
        manageService.updateInfo(slug, principal, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/blogs/{slug}/settings")
    public ResponseEntity<Void> updateSettings(@PathVariable("slug") String slug,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody BlogManageService.SettingsRequest request) {
        manageService.updateSettings(slug, principal, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/blogs/{slug}/share-link")
    public Map<String, String> regenerateShareLink(@PathVariable("slug") String slug,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return Map.of("shareUrl", manageService.regenerateShareLink(slug, principal));
    }

    @GetMapping("/api/blogs/{slug}/members")
    public List<BlogManageService.MemberItem> members(@PathVariable("slug") String slug,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return manageService.members(slug, principal);
    }

    /** 블로그장 전용 멤버 더보기: 글 수·댓글 수·경고 횟수 (D-112). */
    @GetMapping("/api/blogs/{slug}/member-stats")
    public BlogMemberStatsService.Page<BlogMemberStatsService.MemberStat> memberStats(
            @PathVariable("slug") String slug, @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size) {
        return statsService.members(slug, principal, PageParams.of(page, size));
    }

    @GetMapping("/api/blogs/{slug}/members/{userId}/posts")
    public BlogMemberStatsService.Page<BlogMemberStatsService.MemberPost> memberPosts(
            @PathVariable("slug") String slug, @PathVariable("userId") Long userId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "page", required = false) String page) {
        return statsService.posts(slug, userId, principal, PageParams.of(page, "10"));
    }

    @GetMapping("/api/blogs/{slug}/members/{userId}/comments")
    public BlogMemberStatsService.Page<BlogMemberStatsService.MemberComment> memberComments(
            @PathVariable("slug") String slug, @PathVariable("userId") Long userId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "page", required = false) String page) {
        return statsService.comments(slug, userId, principal, PageParams.of(page, "10"));
    }

    @PutMapping("/api/blogs/{slug}/members/{userId}/sub-owner")
    public ResponseEntity<Void> setSubOwner(@PathVariable("slug") String slug, @PathVariable("userId") Long userId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody BlogManageService.SubOwnerRequest request) {
        manageService.setSubOwner(slug, userId, principal, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/blogs/{slug}/membership")
    public ResponseEntity<Void> leave(@PathVariable("slug") String slug,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        manageService.leave(slug, principal);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/blogs/{slug}/transfer")
    public BlogTransferService.TransferItem requestTransfer(@PathVariable("slug") String slug,
            @AuthenticationPrincipal AuthenticatedUser principal, @RequestBody TransferRequest request) {
        return transferService.request(slug, request.toUserId(), principal);
    }

    @GetMapping("/api/blogs/{slug}/transfer")
    public ResponseEntity<BlogTransferService.TransferItem> pendingTransfer(@PathVariable("slug") String slug,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        BlogTransferService.TransferItem item = transferService.pending(slug, principal);
        return item == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(item);
    }

    @DeleteMapping("/api/blogs/{slug}/transfer")
    public ResponseEntity<Void> cancelTransfer(@PathVariable("slug") String slug,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        transferService.cancel(slug, principal);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/me/transfer-requests")
    public List<BlogTransferService.TransferItem> incoming(@AuthenticationPrincipal AuthenticatedUser principal) {
        return transferService.incoming(principal);
    }

    @PostMapping("/api/transfer-requests/{id}/accept")
    public ResponseEntity<Void> accept(@PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        transferService.respond(id, true, principal);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/transfer-requests/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        transferService.respond(id, false, principal);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/blogs/{slug}/close")
    public Map<String, Object> close(@PathVariable("slug") String slug,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        LocalDateTime at = closeService.close(slug, principal);
        return Map.of("closeScheduledAt", Times.toOffset(at));
    }

    @DeleteMapping("/api/blogs/{slug}/close")
    public ResponseEntity<Void> cancelClose(@PathVariable("slug") String slug,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        closeService.cancel(slug, principal);
        return ResponseEntity.noContent().build();
    }
}
