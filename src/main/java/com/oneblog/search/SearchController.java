package com.oneblog.search;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.oneblog.blog.dto.BlogListItem;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.PageParams;
import com.oneblog.member.UserRole;
import com.oneblog.post.dto.PostCard;

/** 통합 검색·메인 피드·최근 검색어 (BRD-08, BRD-09, BLG-03, 6.1). */
@Controller
public class SearchController {

    private final SearchService searchService;
    private final FeedService feedService;
    private final RecentSearchService recentSearchService;

    public SearchController(SearchService searchService, FeedService feedService,
            RecentSearchService recentSearchService) {
        this.searchService = searchService;
        this.feedService = feedService;
        this.recentSearchService = recentSearchService;
    }

    @GetMapping("/search")
    public String page() {
        return "forward:/search.html";
    }

    @GetMapping("/api/search/blogs")
    @ResponseBody
    public SearchPage<BlogListItem> blogs(@RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return searchService.blogs(q, sort, PageParams.of(page, size), viewer(principal));
    }

    @GetMapping("/api/search/posts")
    @ResponseBody
    public SearchPage<PostCard> posts(@RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "target", required = false) String target,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return searchService.posts(q, target, sort, PageParams.of(page, size), viewer(principal));
    }

    @GetMapping("/api/feed")
    @ResponseBody
    public SearchPage<PostCard> feed(@RequestParam(name = "tab", required = false) String tab,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return feedService.feed(tab, PageParams.of(page, size), principal == null ? null : principal.id());
    }

    @GetMapping("/api/me/recent-searches")
    @ResponseBody
    public List<RecentSearchService.RecentSearch> recent(@AuthenticationPrincipal AuthenticatedUser principal) {
        return recentSearchService.list(principal.id());
    }

    @DeleteMapping("/api/me/recent-searches/{id}")
    public ResponseEntity<Void> deleteRecent(@PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        recentSearchService.delete(principal.id(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/me/recent-searches")
    public ResponseEntity<Void> deleteAllRecent(@AuthenticationPrincipal AuthenticatedUser principal) {
        recentSearchService.deleteAll(principal.id());
        return ResponseEntity.noContent().build();
    }

    /** 관리자는 검색어를 남기지 않는다. */
    private static Long viewer(AuthenticatedUser principal) {
        return principal == null || principal.role() == UserRole.ADMIN ? null : principal.id();
    }
}
