package com.oneblog.blog.subscription;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.oneblog.common.security.AuthenticatedUser;

/** 블로그 구독 API (SOC-01, D-50). */
@RestController
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/api/blogs/{slug}/subscription")
    public SubscriptionService.SubscribeState toggle(@PathVariable("slug") String slug,
            @RequestParam(name = "key", required = false) String key,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return subscriptionService.toggle(slug, key, principal);
    }

    @GetMapping("/api/me/subscriptions")
    public List<SubscriptionService.SubscribedBlog> mine(@AuthenticationPrincipal AuthenticatedUser principal) {
        return subscriptionService.mySubscriptions(principal.id());
    }
}
