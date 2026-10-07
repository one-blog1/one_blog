package com.oneblog.blog;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.oneblog.blog.dto.BlogDetailResponse;
import com.oneblog.blog.dto.BlogPageResponse;
import com.oneblog.blog.dto.BlogQuotaResponse;
import com.oneblog.blog.dto.CreateBlogRequest;
import com.oneblog.blog.dto.CreateBlogResponse;
import com.oneblog.blog.dto.MyBlogsResponse;
import com.oneblog.blog.dto.SlugAvailabilityResponse;
import com.oneblog.common.security.AuthenticatedUser;

/**
 * 블로그 API (contracts/blog-api.md). 로그인이 필요한지는 SecurityConfig가 주소로 정한다 (research R10).
 * 도우미 API는 /api/blogs/{slug}와 겹치지 않게 /api/blog-slugs, /api/me 아래에 둔다.
 */
@RestController
public class BlogApiController {

    private final BlogCreateService createService;
    private final BlogQueryService queryService;

    public BlogApiController(BlogCreateService createService, BlogQueryService queryService) {
        this.createService = createService;
        this.queryService = queryService;
    }

    @PostMapping("/api/blogs")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateBlogResponse create(@AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody CreateBlogRequest request) {
        return createService.create(principal, request);
    }

    @GetMapping("/api/blogs")
    public BlogPageResponse list(@RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size) {
        return queryService.list(sort, parseInt(page), parseInt(size));
    }

    @GetMapping("/api/blogs/{slug}")
    public BlogDetailResponse detail(@PathVariable("slug") String slug,
            @RequestParam(name = "key", required = false) String key,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return queryService.detail(slug, key, principal == null ? null : principal.id());
    }

    @GetMapping("/api/blog-slugs/availability")
    public SlugAvailabilityResponse slugAvailability(@RequestParam("slug") String slug) {
        return createService.checkSlug(slug);
    }

    @GetMapping("/api/me/blog-quota")
    public BlogQuotaResponse quota(@AuthenticationPrincipal AuthenticatedUser principal) {
        return createService.quota(principal);
    }

    @GetMapping("/api/me/blogs")
    public MyBlogsResponse myBlogs(@AuthenticationPrincipal AuthenticatedUser principal) {
        return queryService.myBlogs(principal.id());
    }

    /** 숫자가 아니면 null (기본값으로 처리). */
    private static Integer parseInt(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
