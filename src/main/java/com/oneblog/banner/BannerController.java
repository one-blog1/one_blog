package com.oneblog.banner;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
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
import org.springframework.web.multipart.MultipartFile;

import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.file.FileUploadService;
import com.oneblog.file.StoredFile;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 메인 배너 (BRD-12)와 관리자 배너 관리 (ADM-08). /api/admin/**은 주소 규칙에서 ADMIN만 들어온다 (SecurityConfig).
 */
@RestController
public class BannerController {

    private final BannerService bannerService;
    private final BannerAdminService adminService;
    private final FileUploadService uploadService;

    public BannerController(BannerService bannerService, BannerAdminService adminService,
            FileUploadService uploadService) {
        this.bannerService = bannerService;
        this.adminService = adminService;
        this.uploadService = uploadService;
    }

    /** 누구나. 인기 글은 보는 사람이 차단한 회원의 글을 뺀다. */
    @GetMapping("/api/banners")
    public Map<String, List<BannerSlide>> banners(@AuthenticationPrincipal AuthenticatedUser principal) {
        return Map.of("items", bannerService.slides(principal == null ? null : principal.id()));
    }

    @GetMapping("/api/admin/banners")
    public BannerAdminService.Overview overview() {
        return adminService.overview();
    }

    @PostMapping("/api/admin/banners/image")
    public ResponseEntity<Map<String, Object>> uploadImage(@AuthenticationPrincipal AuthenticatedUser admin,
            @RequestParam("file") MultipartFile file) {
        StoredFile stored = uploadService.uploadBannerImage(admin.id(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("fileId", stored.getId(), "url", stored.url()));
    }

    @PostMapping("/api/admin/banners")
    public ResponseEntity<Map<String, Object>> create(@RequestBody BannerAdminService.BannerRequest body,
            @AuthenticationPrincipal AuthenticatedUser admin, HttpServletRequest request) {
        Banner banner = adminService.create(body, admin, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", banner.getId()));
    }

    @PutMapping("/api/admin/banners/{id}")
    public ResponseEntity<Void> update(@PathVariable("id") Long id, @RequestBody BannerAdminService.BannerRequest body,
            @AuthenticationPrincipal AuthenticatedUser admin, HttpServletRequest request) {
        adminService.update(id, body, admin, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/admin/banners/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id, @AuthenticationPrincipal AuthenticatedUser admin,
            HttpServletRequest request) {
        adminService.delete(id, admin, request);
        return ResponseEntity.noContent().build();
    }

    public record MoveRequest(String direction) {
    }

    @PostMapping("/api/admin/banners/{id}/move")
    public ResponseEntity<Void> move(@PathVariable("id") Long id, @RequestBody MoveRequest body,
            @AuthenticationPrincipal AuthenticatedUser admin, HttpServletRequest request) {
        adminService.move(id, body.direction(), admin, request);
        return ResponseEntity.noContent().build();
    }

    /** 자동 종류마다 { count, days }. 빠진 종류는 그대로 둔다. */
    public record SettingsRequest(BannerSettings.Auto popular, BannerSettings.Auto newBlog,
            BannerSettings.Auto popularBlog) {
    }

    @PutMapping("/api/admin/banner-settings")
    public BannerSettings updateSettings(@RequestBody SettingsRequest body,
            @AuthenticationPrincipal AuthenticatedUser admin, HttpServletRequest request) {
        return adminService.updateSettings(body.popular(), body.newBlog(), body.popularBlog(), admin, request);
    }
}
