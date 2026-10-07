package com.oneblog.blog.ops;

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

/** 블로그장 위임 요청 (Crowfoot ERD blog_transfer_requests, BLG-08). 받는 멤버가 수락해야 넘어가고 7일 뒤 자동 취소. */
@Entity
@Table(name = "blog_transfer_requests")
public class BlogTransferRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "to_user_id", nullable = false)
    private Long toUserId;

    @Column(name = "blog_id", nullable = false)
    private Long blogId;

    @Column(name = "from_user_id", nullable = false)
    private Long fromUserId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 20, nullable = false)
    private TransferStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected BlogTransferRequest() {
    }

    public static BlogTransferRequest pending(Long blogId, Long fromUserId, Long toUserId, LocalDateTime expiresAt) {
        BlogTransferRequest request = new BlogTransferRequest();
        request.blogId = blogId;
        request.fromUserId = fromUserId;
        request.toUserId = toUserId;
        request.status = TransferStatus.PENDING;
        request.expiresAt = expiresAt;
        return request;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void finish(TransferStatus result, LocalDateTime now) {
        this.status = result;
        this.respondedAt = now;
    }

    public boolean isPending() {
        return status == TransferStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public Long getToUserId() {
        return toUserId;
    }

    public Long getBlogId() {
        return blogId;
    }

    public Long getFromUserId() {
        return fromUserId;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
