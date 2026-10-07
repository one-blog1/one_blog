package com.oneblog.notification;

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

/** 알림 API (SOC-04, 3.6). 안 읽은 수는 화면이 30초마다 묻는다 — 사용자 활동으로 세지 않는다 (D-62, D-72). */
@RestController
public class NotificationController {

    private final NotificationQueryService service;

    public NotificationController(NotificationQueryService service) {
        this.service = service;
    }

    @GetMapping("/api/notifications")
    public NotificationQueryService.Page list(@AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "tab", required = false) String tab,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size) {
        return service.list(principal, tab, PageParams.of(page, size));
    }

    @GetMapping("/api/notifications/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthenticatedUser principal) {
        return Map.of("count", service.unreadCount(principal));
    }

    @PostMapping("/api/notifications/{id}/read")
    public ResponseEntity<Void> read(@PathVariable("id") Long id, @AuthenticationPrincipal AuthenticatedUser principal) {
        service.markRead(principal, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/notifications/read-all")
    public ResponseEntity<Void> readAll(@AuthenticationPrincipal AuthenticatedUser principal) {
        service.markAllRead(principal);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/notifications")
    public ResponseEntity<Void> deleteAll(@AuthenticationPrincipal AuthenticatedUser principal) {
        service.deleteAll(principal);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/me/notification-settings")
    public NotificationQueryService.Settings settings(@AuthenticationPrincipal AuthenticatedUser principal) {
        return service.settings(principal);
    }

    @PutMapping("/api/me/notification-settings")
    public NotificationQueryService.Settings updateSettings(@AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody NotificationQueryService.SettingsRequest request) {
        return service.updateSettings(principal, request);
    }
}
