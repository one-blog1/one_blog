package com.oneblog.post;

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

import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.PageParams;
import com.oneblog.post.dto.PostDetailResponse;
import com.oneblog.post.dto.PostPageResponse;
import com.oneblog.post.dto.PostRequest;

/** 블로그 글 API (specs/004-posts/plan.md "API"). */
@RestController
public class PostController {

    private final PostService postService;
    private final com.oneblog.view.ViewCounter viewCounter;

    public PostController(PostService postService, com.oneblog.view.ViewCounter viewCounter) {
        this.postService = postService;
        this.viewCounter = viewCounter;
    }

    @GetMapping("/api/blogs/{slug}/posts")
    public PostPageResponse list(@PathVariable("slug") String slug,
            @RequestParam(name = "key", required = false) String key,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size,
            @RequestParam(name = "category", required = false) Long category,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return postService.list(slug, key, principal == null ? null : principal.id(), PageParams.of(page, size),
                category);
    }

    @PostMapping("/api/blogs/{slug}/posts")
    public ResponseEntity<Map<String, Object>> create(@PathVariable("slug") String slug,
            @RequestParam(name = "key", required = false) String key,
            @AuthenticationPrincipal AuthenticatedUser principal, @RequestBody PostRequest request) {
        Post post = postService.create(slug, key, principal, request);
        String blogSlug = postService.blogOf(post).getSlug();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", post.getId(),
                "url", "/blog/" + blogSlug + "/posts/" + post.getId()));
    }

    /** 글 상세. 볼 수 있는 글이면 조회수를 센다 (BRD-11). 관리자의 조회는 세지 않는다. */
    @GetMapping("/api/posts/{id}")
    public PostDetailResponse detail(@PathVariable("id") Long id,
            @RequestParam(name = "key", required = false) String key,
            @AuthenticationPrincipal AuthenticatedUser principal, jakarta.servlet.http.HttpServletRequest request) {
        PostDetailResponse detail = postService.detail(id, key, principal);
        if (principal != null && principal.role() == com.oneblog.member.UserRole.ADMIN) {
            return detail;
        }
        boolean counted = viewCounter.record(id, principal == null ? null : principal.id(), request.getRemoteAddr(),
                request.getHeader("User-Agent"));
        return counted ? detail.withViewCount(detail.viewCount() + 1) : detail;
    }

    @PutMapping("/api/posts/{id}")
    public Map<String, Object> update(@PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser principal, @RequestBody PostRequest request) {
        Post post = postService.update(id, principal, request);
        return Map.of("id", post.getId());
    }

    @DeleteMapping("/api/posts/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        postService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }
}
