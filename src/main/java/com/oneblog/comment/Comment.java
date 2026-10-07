package com.oneblog.comment;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** 댓글과 1단계 대댓글 (Crowfoot ERD comments, BRD-06, D-78, D-87). */
@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "reply_to_user_id")
    private Long replyToUserId;

    @Column(name = "content", length = 500, nullable = false)
    private String content;

    @Column(name = "is_edited", nullable = false)
    private boolean edited;

    @Column(name = "is_hidden", nullable = false)
    private boolean hidden;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Comment() {
    }

    public static Comment create(Long postId, Long userId, Long parentId, Long replyToUserId, String content) {
        Comment comment = new Comment();
        comment.postId = postId;
        comment.userId = userId;
        comment.parentId = parentId;
        comment.replyToUserId = replyToUserId;
        comment.content = content;
        return comment;
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

    public void edit(String content) {
        this.content = content;
        this.edited = true;
    }

    public void delete() {
        if (this.deletedAt == null) {
            this.deletedAt = LocalDateTime.now();
        }
    }

    public void hide(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isReply() {
        return parentId != null;
    }

    public Long getId() {
        return id;
    }

    public Long getParentId() {
        return parentId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getReplyToUserId() {
        return replyToUserId;
    }

    public String getContent() {
        return content;
    }

    public boolean isEdited() {
        return edited;
    }

    public boolean isHidden() {
        return hidden;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
