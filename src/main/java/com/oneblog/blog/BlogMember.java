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
 * 블로그 멤버십 (Crowfoot ERD blog_members). 회원과 블로그를 잇고 블로그별 역할을 둔다 (2장, constitution IV).
 * 부블로그장 권한·정지 컬럼은 그 기능에서 매핑한다.
 */
@Entity
@Table(name = "blog_members")
public class BlogMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "blog_id", nullable = false)
    private Long blogId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", length = 20, nullable = false)
    private BlogRole role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 20, nullable = false)
    private BlogMemberStatus status;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected BlogMember() {
    }

    /** 블로그를 만든 회원을 블로그장으로 등록한다 (BLG-01). */
    public static BlogMember owner(Long blogId, Long userId) {
        BlogMember member = new BlogMember();
        member.blogId = blogId;
        member.userId = userId;
        member.role = BlogRole.OWNER;
        member.status = BlogMemberStatus.ACTIVE;
        return member;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.joinedAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
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

    public BlogRole getRole() {
        return role;
    }

    public BlogMemberStatus getStatus() {
        return status;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }
}
