package com.oneblog.blog.subscription;

/** 블로그를 새로 구독했을 때 (알림 011: 블로그장에게 BLOG_SUBSCRIBE). */
public interface SubscriptionListener {

    void subscribed(Long blogId, Long userId);
}
