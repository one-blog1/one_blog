package com.oneblog.sanction;

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

/** 제재 기록 (Crowfoot ERD sanctions, 3.7). 횟수는 블로그 + 사람 기준으로 세고 1년 보관한다. */
@Entity
@Table(name = "sanctions")
public class Sanction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "blog_id", nullable = false)
    private Long blogId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", length = 20, nullable = false)
    private SanctionType type;

    @Column(name = "issuer_type", length = 20, nullable = false)
    private String issuerType;

    @Column(name = "issued_by_user_id", nullable = false)
    private Long issuedByUserId;

    @Column(name = "reason", length = 500, nullable = false)
    private String reason;

    @Column(name = "duration_days")
    private Short durationDays;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Sanction() {
    }

    public static Sanction of(Long blogId, Long userId, SanctionType type, boolean byAdmin, Long issuedBy, String reason,
            Long reportId) {
        Sanction sanction = new Sanction();
        sanction.blogId = blogId;
        sanction.userId = userId;
        sanction.type = type;
        sanction.issuerType = byAdmin ? "ADMIN" : "BLOG";
        sanction.issuedByUserId = issuedBy;
        sanction.reason = reason;
        sanction.reportId = reportId;
        return sanction;
    }

    /** 정지 기간. days가 null이면 영구 (9999-12-31). */
    public Sanction suspendFor(Integer days, LocalDateTime now) {
        this.durationDays = days == null ? null : days.shortValue();
        this.endsAt = days == null ? LocalDateTime.of(9999, 12, 31, 0, 0) : now.plusDays(days);
        return this;
    }

    public void release(LocalDateTime now) {
        this.releasedAt = now;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getEndsAt() {
        return endsAt;
    }

    public SanctionType getType() {
        return type;
    }
}
