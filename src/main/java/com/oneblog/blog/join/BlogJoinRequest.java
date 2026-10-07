package com.oneblog.blog.join;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** 승인제 블로그의 참여 신청 (Crowfoot ERD blog_join_requests, BLG-04·05). */
@Entity
@Table(name = "blog_join_requests")
public class BlogJoinRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "blog_id", nullable = false)
    private Long blogId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 20, nullable = false)
    private JoinRequestStatus status;

    @Column(name = "processed_by_user_id")
    private Long processedByUserId;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected BlogJoinRequest() {
    }

    public static BlogJoinRequest pending(Long blogId, Long userId) {
        BlogJoinRequest request = new BlogJoinRequest();
        request.blogId = blogId;
        request.userId = userId;
        request.status = JoinRequestStatus.PENDING;
        return request;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isPending() {
        return status == JoinRequestStatus.PENDING;
    }

    public void approve(Long managerId) {
        process(JoinRequestStatus.APPROVED, managerId);
    }

    public void reject(Long managerId) {
        process(JoinRequestStatus.REJECTED, managerId);
    }

    /** 신청한 본인이 취소. 처리한 사람은 신청자 본인으로 남긴다. */
    public void cancel() {
        process(JoinRequestStatus.CANCELED, userId);
    }

    private void process(JoinRequestStatus next, Long by) {
        this.status = next;
        this.processedByUserId = by;
        this.processedAt = LocalDateTime.now();
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

    public JoinRequestStatus getStatus() {
        return status;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
