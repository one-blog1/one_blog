package com.oneblog.blog;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * 블로그 (Crowfoot ERD blogs).
 */
@Entity
@Table(name = "blogs")
public class Blog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slug", length = 30)
    private String slug;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "visibility", length = 20, nullable = false)
    private BlogVisibility visibility;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "join_policy", length = 20, nullable = false)
    private BlogJoinPolicy joinPolicy;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "share_token", length = 32)
    private String shareToken;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 20, nullable = false)
    private BlogStatus status;

    @Column(name = "is_hidden", nullable = false)
    private boolean hidden;

    @Column(name = "close_scheduled_at")
    private LocalDateTime closeScheduledAt;

    @Column(name = "close_reason", length = 20)
    private String closeReason;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "member_count", nullable = false)
    private int memberCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Blog() {
    }

    /** 새 블로그 (BLG-01). 입력값은 BlogPolicy로 정리·검증된 값이어야 한다. 만든 회원 1명이 멤버 수 1. */
    public static Blog create(String slug, String name, String description, String coverImageUrl,
            BlogVisibility visibility, BlogJoinPolicy joinPolicy, String shareToken) {
        Blog blog = new Blog();
        blog.slug = slug;
        blog.name = name;
        blog.description = description;
        blog.coverImageUrl = coverImageUrl;
        blog.visibility = visibility;
        blog.joinPolicy = joinPolicy;
        blog.shareToken = visibility == BlogVisibility.UNLISTED ? shareToken : null;
        blog.status = BlogStatus.ACTIVE;
        blog.hidden = false;
        blog.memberCount = 1;
        return blog;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** 관리자 숨김 (ADM-02). 숨긴 블로그는 목록·검색에서 빠지고 멤버만 들어갈 수 있다. */
    public void hide(boolean hidden) {
        this.hidden = hidden;
    }

    /** 정보 수정 (2장). 값은 BlogPolicy로 정리·검사한 값. */
    public void updateInfo(String name, String description, String coverImageUrl) {
        this.name = name;
        this.description = description;
        this.coverImageUrl = coverImageUrl;
    }

    /** 공개 범위 바꾸기. 일부 공개가 되면 새 공유 토큰, 아니면 토큰을 비운다 (BLG-01). */
    public void changeVisibility(BlogVisibility visibility, String newShareToken) {
        this.visibility = visibility;
        if (visibility != BlogVisibility.UNLISTED) {
            this.shareToken = null;
        } else if (this.shareToken == null) {
            this.shareToken = newShareToken;
        }
    }

    /** 공유 링크 다시 만들기. 이전 링크는 바로 무효가 된다 (BLG-01). */
    public void regenerateShareToken(String newShareToken) {
        if (visibility == BlogVisibility.UNLISTED) {
            this.shareToken = newShareToken;
        }
    }

    public void changeJoinPolicy(BlogJoinPolicy joinPolicy) {
        this.joinPolicy = joinPolicy;
    }

    /** 폐쇄 예약 (BLG-09, ADM-07). reason: OWNER, ADMIN, OWNER_REVOKED. */
    public void scheduleClose(LocalDateTime at, String reason) {
        this.status = BlogStatus.CLOSING;
        this.closeScheduledAt = at;
        this.closeReason = reason;
    }

    /** 폐쇄 철회 (BLG-09). */
    public void cancelClose() {
        this.status = BlogStatus.ACTIVE;
        this.closeScheduledAt = null;
        this.closeReason = null;
    }

    /** 04:00 배치가 폐쇄한다. */
    public void close(LocalDateTime now) {
        this.status = BlogStatus.CLOSED;
        this.closedAt = now;
    }

    /** 폐쇄 30일 뒤: 주소를 비워 다른 사람이 쓰게 하고 행은 남긴다 (D-86). */
    public void purge(LocalDateTime now) {
        this.slug = null;
        this.shareToken = null;
        this.deletedAt = now;
    }

    public boolean isClosing() {
        return status == BlogStatus.CLOSING;
    }

    public LocalDateTime getCloseScheduledAt() {
        return closeScheduledAt;
    }

    public String getCloseReason() {
        return closeReason;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    /** 폐쇄·삭제되지 않은 블로그 (폐쇄 예정 중에도 평소처럼 운영, D-67). */
    public boolean isOpen() {
        return status != BlogStatus.CLOSED && deletedAt == null;
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public BlogVisibility getVisibility() {
        return visibility;
    }

    public BlogJoinPolicy getJoinPolicy() {
        return joinPolicy;
    }

    public String getShareToken() {
        return shareToken;
    }

    public BlogStatus getStatus() {
        return status;
    }

    public boolean isHidden() {
        return hidden;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
