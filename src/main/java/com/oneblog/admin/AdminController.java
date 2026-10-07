package com.oneblog.admin;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.PageParams;
import com.oneblog.post.Post;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 관리자 API (/api/admin/**). 주소 규칙에서 ADMIN 권한만 들어온다 (SecurityConfig, SEC-11).
 * 조치는 모두 활동 기록을 남긴다 (ADM-06).
 */
@RestController
public class AdminController {

    private final AdminService adminService;
    private final NoticeService noticeService;

    public AdminController(AdminService adminService, NoticeService noticeService) {
        this.adminService = adminService;
        this.noticeService = noticeService;
    }

    /** 숨기기·풀기·삭제 요청. reason은 기록에 남는다. */
    public record ActionRequest(Boolean hidden, String reason) {
    }

    public record NoticeRequest(String title, String content) {
    }

    @GetMapping("/api/admin/users")
    public AdminPage<AdminService.UserRow> users(@RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size) {
        return adminService.users(q, PageParams.of(page, size));
    }

    @GetMapping("/api/admin/users/{id}")
    public AdminService.UserDetail user(@PathVariable("id") Long id) {
        return adminService.user(id);
    }

    @PostMapping("/api/admin/users/{id}/reveal")
    public AdminService.PersonalInfo reveal(@PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser admin, HttpServletRequest request) {
        return adminService.revealUser(id, admin, request);
    }

    @GetMapping("/api/admin/blogs")
    public AdminPage<AdminService.BlogRow> blogs(@RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size) {
        return adminService.blogs(q, PageParams.of(page, size));
    }

    @PostMapping("/api/admin/blogs/{id}/hide")
    public ResponseEntity<Void> hideBlog(@PathVariable("id") Long id, @RequestBody ActionRequest body,
            @AuthenticationPrincipal AuthenticatedUser admin, HttpServletRequest request) {
        adminService.hideBlog(id, !Boolean.FALSE.equals(body.hidden()), body.reason(), admin, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/admin/posts")
    public AdminPage<AdminService.PostRow> posts(@RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size) {
        return adminService.posts(q, PageParams.of(page, size));
    }

    @PostMapping("/api/admin/posts/{id}/hide")
    public ResponseEntity<Void> hidePost(@PathVariable("id") Long id, @RequestBody ActionRequest body,
            @AuthenticationPrincipal AuthenticatedUser admin, HttpServletRequest request) {
        adminService.hidePost(id, !Boolean.FALSE.equals(body.hidden()), body.reason(), admin, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/posts/{id}/delete")
    public ResponseEntity<Void> deletePost(@PathVariable("id") Long id, @RequestBody ActionRequest body,
            @AuthenticationPrincipal AuthenticatedUser admin, HttpServletRequest request) {
        adminService.deletePost(id, body.reason(), admin, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/admin/comments")
    public AdminPage<AdminService.CommentRow> comments(@RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size) {
        return adminService.comments(q, PageParams.of(page, size));
    }

    @PostMapping("/api/admin/comments/{id}/hide")
    public ResponseEntity<Void> hideComment(@PathVariable("id") Long id, @RequestBody ActionRequest body,
            @AuthenticationPrincipal AuthenticatedUser admin, HttpServletRequest request) {
        adminService.hideComment(id, !Boolean.FALSE.equals(body.hidden()), body.reason(), admin, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/comments/{id}/delete")
    public ResponseEntity<Void> deleteComment(@PathVariable("id") Long id, @RequestBody ActionRequest body,
            @AuthenticationPrincipal AuthenticatedUser admin, HttpServletRequest request) {
        adminService.deleteComment(id, body.reason(), admin, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/admin/stats")
    public AdminService.Stats stats(@RequestParam(name = "days", required = false, defaultValue = "30") int days) {
        return adminService.stats(days);
    }

    @GetMapping("/api/admin/actions")
    public AdminPage<AdminService.ActionRow> actions(@RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size) {
        return adminService.actions(PageParams.of(page, size));
    }

    @PostMapping("/api/admin/notices")
    public ResponseEntity<Map<String, Object>> createNotice(@RequestBody NoticeRequest body,
            @AuthenticationPrincipal AuthenticatedUser admin, HttpServletRequest request) {
        Post notice = noticeService.create(body.title(), body.content(), admin, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", notice.getId()));
    }

    @DeleteMapping("/api/admin/notices/{id}")
    public ResponseEntity<Void> deleteNotice(@PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser admin, HttpServletRequest request) {
        noticeService.delete(id, admin, request);
        return ResponseEntity.noContent().build();
    }
}
