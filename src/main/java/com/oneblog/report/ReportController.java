package com.oneblog.report;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.oneblog.admin.AdminPage;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.PageParams;
import com.oneblog.sanction.OwnerSanctionService;

import jakarta.servlet.http.HttpServletRequest;

/** 신고와 처리 API (SOC-06, ADM-04, BLG-13), 관리자의 블로그장 제재·강제 폐쇄 (ADM-02, ADM-07). */
@RestController
public class ReportController {

    private final ReportService reportService;
    private final OwnerSanctionService ownerSanctionService;

    public ReportController(ReportService reportService, OwnerSanctionService ownerSanctionService) {
        this.reportService = reportService;
        this.ownerSanctionService = ownerSanctionService;
    }

    public record ReasonRequest(String reason) {
    }

    @PostMapping("/api/reports")
    public ResponseEntity<Map<String, Long>> report(@AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody ReportService.ReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", reportService.create(principal, request)));
    }

    @GetMapping("/api/blogs/{slug}/reports")
    public List<ReportService.ReportItem> blogReports(@PathVariable("slug") String slug,
            @RequestParam(name = "status", required = false) String status,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return reportService.listForBlog(slug, principal, status);
    }

    @PostMapping("/api/blogs/{slug}/reports/{id}/resolve")
    public ResponseEntity<Void> resolveBlogReport(@PathVariable("slug") String slug, @PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser principal, @RequestBody ReportService.ResolveRequest request) {
        reportService.resolveForBlog(slug, id, principal, request);
        return ResponseEntity.noContent().build();
    }

    // ---- 메인 관리자 (/api/admin/**는 SecurityConfig에서 ADMIN만) ----

    @GetMapping("/api/admin/reports")
    public AdminPage<ReportService.ReportItem> adminReports(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size) {
        return reportService.listForAdmin(status, PageParams.of(page, size));
    }

    @PostMapping("/api/admin/reports/{id}/resolve")
    public ResponseEntity<Void> resolveAdminReport(@PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser admin, @RequestBody ReportService.ResolveRequest request,
            HttpServletRequest httpRequest) {
        reportService.resolveForAdmin(id, admin, request, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/blogs/{id}/close")
    public ResponseEntity<Void> forceClose(@PathVariable("id") Long id, @AuthenticationPrincipal AuthenticatedUser admin,
            @RequestBody ReasonRequest request, HttpServletRequest httpRequest) {
        ownerSanctionService.forceClose(id, request.reason(), admin, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/blogs/{id}/owner-warning")
    public OwnerSanctionService.Result warnOwner(@PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser admin, @RequestBody ReasonRequest request,
            HttpServletRequest httpRequest) {
        return ownerSanctionService.warnOwner(id, request.reason(), null, admin, httpRequest);
    }

    @PostMapping("/api/admin/blogs/{id}/owner-revoke")
    public OwnerSanctionService.Result revokeOwner(@PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser admin, @RequestBody ReasonRequest request,
            HttpServletRequest httpRequest) {
        return ownerSanctionService.revokeOwner(id, request.reason(), null, admin, httpRequest);
    }
}
