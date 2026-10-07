package com.oneblog.blog.ops;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogMemberRepository;
import com.oneblog.blog.subscription.BlogSubscriptionRepository;
import com.oneblog.notification.NotificationService;
import com.oneblog.notification.NotificationType;

/** 블로그 알림을 받을 사람: 블로그장을 뺀 멤버 전원(부블로그장 포함)과 구독자 (BLG-09, D-14). */
@Component
public class BlogAudience {

    private final BlogMemberRepository memberRepository;
    private final BlogSubscriptionRepository subscriptionRepository;
    private final NotificationService notifications;

    public BlogAudience(BlogMemberRepository memberRepository, BlogSubscriptionRepository subscriptionRepository,
            NotificationService notifications) {
        this.memberRepository = memberRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.notifications = notifications;
    }

    public Set<Long> membersAndSubscribers(Blog blog) {
        Set<Long> ids = new LinkedHashSet<>(memberRepository.findMemberIdsExceptOwner(blog.getId()));
        ids.addAll(subscriptionRepository.findUserIds(blog.getId()));
        Long ownerId = memberRepository.findOwnerId(blog.getId());
        if (ownerId != null) {
            ids.remove(ownerId);
        }
        return ids;
    }

    public void notifyAll(Blog blog, NotificationType type, String message, String dedupeKey) {
        notifications.sendAll(membersAndSubscribers(blog), type, message,
                blog.getSlug() == null ? null : "/blog/" + blog.getSlug(), dedupeKey);
    }
}
