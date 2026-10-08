package com.oneblog.social;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.PageParams;
import com.oneblog.profile.PrivacyService;

/** 팔로우 API (SOC-01, SOC-02). */
@RestController
public class FollowController {

    private final FollowService followService;

    private final PrivacyService privacyService;

    public FollowController(FollowService followService, PrivacyService privacyService) {
        this.followService = followService;
        this.privacyService = privacyService;
    }

    @PostMapping("/api/users/{nickname}/follow")
    public FollowService.FollowState toggle(@PathVariable("nickname") String nickname,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return followService.toggle(principal, nickname);
    }

    /** 팔로워·팔로잉 목록은 본인이 공개로 둔 경우에만 남에게 보인다 (D-114). */
    @GetMapping("/api/users/{nickname}/followers")
    public FollowService.UserPage followers(@PathVariable("nickname") String nickname,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        privacyService.requireFollowsVisible(followService.findMember(nickname), principal == null ? null : principal.id());
        return followService.followers(nickname, PageParams.of(page, size));
    }

    @GetMapping("/api/users/{nickname}/following")
    public FollowService.UserPage following(@PathVariable("nickname") String nickname,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        privacyService.requireFollowsVisible(followService.findMember(nickname), principal == null ? null : principal.id());
        return followService.following(nickname, PageParams.of(page, size));
    }
}
