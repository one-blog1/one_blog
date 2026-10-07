package com.oneblog.sanction;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.oneblog.common.security.AuthenticatedUser;

/** 블로그장의 멤버 제재·블랙리스트·해제 문의 API (BLG-11~13). */
@RestController
public class SanctionController {

    private final SanctionService sanctionService;
    private final BlacklistService blacklistService;

    public SanctionController(SanctionService sanctionService, BlacklistService blacklistService) {
        this.sanctionService = sanctionService;
        this.blacklistService = blacklistService;
    }

    public record InquiryRequest(String message) {
    }

    @PostMapping("/api/blogs/{slug}/members/{userId}/sanctions")
    public ResponseEntity<Void> sanction(@PathVariable("slug") String slug, @PathVariable("userId") Long userId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody SanctionService.SanctionRequest request) {
        sanctionService.apply(slug, userId, principal, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/blogs/{slug}/blacklist")
    public List<BlacklistService.BlacklistItem> blacklist(@PathVariable("slug") String slug,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return blacklistService.list(slug, principal);
    }

    @PostMapping("/api/blogs/{slug}/blacklist-inquiries")
    public ResponseEntity<Void> inquire(@PathVariable("slug") String slug,
            @AuthenticationPrincipal AuthenticatedUser principal, @RequestBody InquiryRequest request) {
        blacklistService.inquire(slug, principal, request.message());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/api/blogs/{slug}/blacklist-inquiries")
    public List<BlacklistService.InquiryItem> inquiries(@PathVariable("slug") String slug,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return blacklistService.inquiries(slug, principal);
    }

    @PostMapping("/api/blogs/{slug}/blacklist-inquiries/{id}/release")
    public ResponseEntity<Void> release(@PathVariable("slug") String slug, @PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        blacklistService.resolveInquiry(slug, id, true, principal);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/blogs/{slug}/blacklist-inquiries/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable("slug") String slug, @PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        blacklistService.resolveInquiry(slug, id, false, principal);
        return ResponseEntity.noContent().build();
    }
}
