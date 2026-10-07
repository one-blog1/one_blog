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
 * 블로그 (Crowfoot ERD blogs). 폐쇄 관련 컬럼(close_*)은 012에서 매핑한다.
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
