package com.oneblog.blog.subscription;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** 블로그 구독 (Crowfoot ERD blog_subscriptions, SOC-01, D-02). */
@Entity
@Table(name = "blog_subscriptions")
public class BlogSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "blog_id", nullable = false)
    private Long blogId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected BlogSubscription() {
    }

    public static BlogSubscription of(Long blogId, Long userId) {
        BlogSubscription subscription = new BlogSubscription();
        subscription.blogId = blogId;
        subscription.userId = userId;
        return subscription;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getBlogId() {
        return blogId;
    }
}
