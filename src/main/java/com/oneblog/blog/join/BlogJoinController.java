package com.oneblog.blog.join;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.oneblog.common.security.AuthenticatedUser;

/** 참여 신청·승인 API (specs/003-blog-join/plan.md "API"). 모두 로그인이 필요하다. */
@RestController
public class BlogJoinController {

    private final BlogJoinService joinService;

    public BlogJoinController(BlogJoinService joinService) {
        this.joinService = joinService;
    }

    @GetMapping("/api/blogs/{slug}/join")
    public JoinStatusResponse status(@PathVariable("slug") String slug,
            @RequestParam(name = "key", required = false) String key,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return joinService.status(slug, key, principal);
    }

    /** 자유 참여면 201(MEMBER), 승인제면 202(PENDING). */
    @PostMapping("/api/blogs/{slug}/join")
    public ResponseEntity<JoinStatusResponse> apply(@PathVariable("slug") String slug,
            @RequestParam(name = "key", required = false) String key,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        JoinStatusResponse result = joinService.apply(slug, key, principal);
        return ResponseEntity.status("MEMBER".equals(result.status()) ? 201 : 202).body(result);
    }

    @DeleteMapping("/api/blogs/{slug}/join")
    public JoinStatusResponse cancel(@PathVariable("slug") String slug,
            @RequestParam(name = "key", required = false) String key,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return joinService.cancel(slug, key, principal);
    }

    /** 내가 신청하고 기다리는 블로그 목록 (D-111). */
    @GetMapping("/api/me/join-requests")
    public List<BlogJoinService.MyJoinRequest> myRequests(@AuthenticationPrincipal AuthenticatedUser principal) {
        return joinService.myPendingRequests(principal);
    }

    @DeleteMapping("/api/me/join-requests/{id}")
    public ResponseEntity<Void> cancelMine(@PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        joinService.cancelMine(id, principal);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/blogs/{slug}/join-requests")
    public List<JoinRequestItem> pending(@PathVariable("slug") String slug,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return joinService.pendingRequests(slug, principal);
    }

    @PostMapping("/api/blogs/{slug}/join-requests/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable("slug") String slug, @PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        joinService.approve(slug, id, principal);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/blogs/{slug}/join-requests/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable("slug") String slug, @PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        joinService.reject(slug, id, principal);
        return ResponseEntity.noContent().build();
    }
}
