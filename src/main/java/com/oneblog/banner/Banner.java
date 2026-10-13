package com.oneblog.banner;

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

/** 관리자가 넣는 메인 배너: 광고·공지·이벤트 (Crowfoot ERD banners, BRD-12, ADM-08, D-132, D-133). 자동 배너는 여기 없다. */
@Entity
@Table(name = "banners")
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false, updatable = false)
    private Long adminId;

    @Column(name = "image_file_id", nullable = false)
    private Long imageFileId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "kind", length = 20, nullable = false)
    private BannerKind kind;

    @Column(name = "title", length = 100, nullable = false)
    private String title;

    @Column(name = "caption", length = 100)
    private String caption;

    @Column(name = "link_url", length = 500, nullable = false)
    private String linkUrl;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Column(name = "is_visible", nullable = false)
    private boolean visible;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Banner() {
    }

    public static Banner create(Long adminId, BannerKind kind, Long imageFileId, String title, String caption,
            String linkUrl, boolean visible, int sortOrder) {
        Banner banner = new Banner();
        banner.adminId = adminId;
        banner.change(kind, imageFileId, title, caption, linkUrl, visible);
        banner.sortOrder = (short) sortOrder;
        return banner;
    }

    public void change(BannerKind kind, Long imageFileId, String title, String caption, String linkUrl,
            boolean visible) {
        this.kind = kind;
        this.imageFileId = imageFileId;
        this.title = title;
        this.caption = caption;
        this.linkUrl = linkUrl;
        this.visible = visible;
    }

    public void moveTo(int sortOrder) {
        this.sortOrder = (short) sortOrder;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getAdminId() {
        return adminId;
    }

    public Long getImageFileId() {
        return imageFileId;
    }

    public BannerKind getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public String getCaption() {
        return caption;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isVisible() {
        return visible;
    }
}
