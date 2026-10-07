package com.oneblog.post;

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

/** 글 (Crowfoot ERD posts). 본문은 마크다운 원문으로 저장하고, 보여줄 때 걸러낸 HTML로 바꾼다 (SEC-06). */
@Entity
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "blog_id")
    private Long blogId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "post_type", length = 20, nullable = false)
    private PostType postType;

    @Column(name = "title", length = 30, nullable = false)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "author_detached", nullable = false)
    private boolean authorDetached;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "is_hidden", nullable = false)
    private boolean hidden;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "deleted_by", length = 20)
    private DeletedBy deletedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Post() {
    }

    public static Post create(Long blogId, Long userId, PostType type, String title, String content) {
        Post post = new Post();
        post.blogId = blogId;
        post.userId = userId;
        post.postType = type;
        post.title = title;
        post.content = content;
        return post;
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

    public void edit(String title, String content) {
        this.title = title;
        this.content = content;
    }

    /** 소프트 삭제 (4.5: 30일 동안 안 보이고, 04:00 배치가 완전 삭제). */
    public void delete(DeletedBy by) {
        this.deletedBy = by;
        this.deletedAt = LocalDateTime.now();
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public void hide(boolean hidden) {
        this.hidden = hidden;
    }

    /** 지워지지 않았고 관리자가 숨기지 않은 글. */
    public boolean isVisible() {
        return deletedAt == null && !hidden;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isNotice() {
        return postType == PostType.BLOG_NOTICE || postType == PostType.MAIN_NOTICE;
    }

    public Long getId() {
        return id;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getBlogId() {
        return blogId;
    }

    public PostType getPostType() {
        return postType;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public boolean isAuthorDetached() {
        return authorDetached;
    }

    public int getViewCount() {
        return viewCount;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public boolean isHidden() {
        return hidden;
    }

    public DeletedBy getDeletedBy() {
        return deletedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
