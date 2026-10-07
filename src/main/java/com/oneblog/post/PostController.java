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

    public PostController(PostService postService) {
        this.postService = postService;
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

    @GetMapping("/api/posts/{id}")
    public PostDetailResponse detail(@PathVariable("id") Long id,
            @RequestParam(name = "key", required = false) String key,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return postService.detail(id, key, principal);
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
