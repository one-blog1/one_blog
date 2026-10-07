package com.oneblog.file;

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

/** 업로드 파일 (Crowfoot ERD files). 실제 바이트는 FileStorage가 저장한다 (SCL-02). */
@Entity
@Table(name = "files")
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id")
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "purpose", length = 20, nullable = false)
    private FilePurpose purpose;

    @Column(name = "stored_name", length = 64, nullable = false)
    private String storedName;

    @Column(name = "original_name", length = 255, nullable = false)
    private String originalName;

    @Column(name = "content_type", length = 50, nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected StoredFile() {
    }

    public static StoredFile create(Long userId, FilePurpose purpose, String storedName, String originalName,
            String contentType, int sizeBytes) {
        StoredFile file = new StoredFile();
        file.userId = userId;
        file.purpose = purpose;
        file.storedName = storedName;
        file.originalName = originalName;
        file.contentType = contentType;
        file.sizeBytes = sizeBytes;
        file.sortOrder = 0;
        return file;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** 화면이 이미지를 받을 주소. */
    public String url() {
        return "/files/" + storedName;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public FilePurpose getPurpose() {
        return purpose;
    }

    public String getStoredName() {
        return storedName;
    }

    public String getContentType() {
        return contentType;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }
}
