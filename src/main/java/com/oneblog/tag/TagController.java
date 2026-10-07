package com.oneblog.tag;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.PageParams;
import com.oneblog.post.dto.PostCardPage;

/** 태그 화면 /tags/{태그}와 그 글 목록 API (BRD-04). */
@Controller
public class TagController {

    private final TagPostService tagPostService;

    public TagController(TagPostService tagPostService) {
        this.tagPostService = tagPostService;
    }

    @GetMapping("/tags/{name}")
    public String page(@PathVariable("name") String name) {
        return "forward:/tag.html";
    }

    @GetMapping("/api/tags/{name}/posts")
    @ResponseBody
    public PostCardPage posts(@PathVariable("name") String name,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return tagPostService.posts(name, principal == null ? null : principal.id(), PageParams.of(page, size));
    }
}
