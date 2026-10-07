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
 * 역할과 부블로그장 권한(D-71)은 요청마다 이 행으로 확인한다 (SEC-07).
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

    @Column(name = "can_edit_info", nullable = false)
    private boolean canEditInfo;

    @Column(name = "can_manage_members", nullable = false)
    private boolean canManageMembers;

    @Column(name = "can_manage_posts", nullable = false)
    private boolean canManagePosts;

    @Column(name = "sub_owner_since")
    private LocalDateTime subOwnerSince;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 20, nullable = false)
    private BlogMemberStatus status;

    @Column(name = "suspended_until")
    private LocalDateTime suspendedUntil;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

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

    /** 참여 신청이 받아들여진 회원을 일반 멤버로 등록한다 (BLG-04, BLG-05). */
    public static BlogMember member(Long blogId, Long userId) {
        BlogMember member = new BlogMember();
        member.blogId = blogId;
        member.userId = userId;
        member.role = BlogRole.MEMBER;
        member.status = BlogMemberStatus.ACTIVE;
        return member;
    }

    /**
     * 블로그를 떠났던 회원이 다시 참여한다. 같은 블로그·회원의 행은 하나뿐이라(uk_blog_members_blog_id_user_id)
     * 새 행 대신 이 행을 일반 멤버로 되살린다. 예전 역할·권한은 이어받지 않는다.
     */
    public void rejoin() {
        this.role = BlogRole.MEMBER;
        this.status = BlogMemberStatus.ACTIVE;
        this.canEditInfo = false;
        this.canManageMembers = false;
        this.canManagePosts = false;
        this.subOwnerSince = null;
        this.leftAt = null;
        this.joinedAt = LocalDateTime.now();
    }

    /** 블로그를 떠난다 (BLG-07) 또는 회원탈퇴로 모든 블로그에서 빠진다 (USR-05). */
    public void leave(LocalDateTime now) {
        this.status = BlogMemberStatus.LEFT;
        this.leftAt = now;
        clearPermissions();
    }

    /** 부블로그장 지정·권한 변경 (2장, D-71). 처음 지정될 때만 지정 시각을 남긴다 (ADM-07 승계 순서). */
    public void makeSubOwner(boolean canEditInfo, boolean canManageMembers, boolean canManagePosts, LocalDateTime now) {
        if (this.role != BlogRole.SUB_OWNER) {
            this.subOwnerSince = now;
        }
        this.role = BlogRole.SUB_OWNER;
        this.canEditInfo = canEditInfo;
        this.canManageMembers = canManageMembers;
        this.canManagePosts = canManagePosts;
    }

    /** 일반 멤버로 (부블로그장 해제, 위임한 예전 블로그장, 권한 박탈된 블로그장 ADM-07). */
    public void makeMember() {
        this.role = BlogRole.MEMBER;
        clearPermissions();
    }

    /** 블로그장이 된다 (위임 수락 BLG-08, 승계 ADM-07). */
    public void makeOwner() {
        this.role = BlogRole.OWNER;
        clearPermissions();
    }

    private void clearPermissions() {
        this.canEditInfo = false;
        this.canManageMembers = false;
        this.canManagePosts = false;
        this.subOwnerSince = null;
    }

    /** 부블로그장에게 준 권한 값 그대로 (화면 표시용). 블로그장 여부는 따지지 않는다. */
    public boolean grantedEditInfo() {
        return canEditInfo;
    }

    public boolean grantedManageMembers() {
        return canManageMembers;
    }

    public boolean grantedManagePosts() {
        return canManagePosts;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.joinedAt == null) {
            this.joinedAt = now;
        }
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

    public boolean isActive() {
        return status == BlogMemberStatus.ACTIVE;
    }

    public boolean isOwner() {
        return isActive() && role == BlogRole.OWNER;
    }

    /** 멤버 관리(참여 승인·거절, 제재)를 할 수 있는지: 블로그장, 또는 멤버 관리 권한을 받은 부블로그장 (2장, D-71). */
    public boolean canManageMembers() {
        return isActive() && (role == BlogRole.OWNER || (role == BlogRole.SUB_OWNER && canManageMembers));
    }

    /** 글 관리(남의 글 삭제·숨김, 공지)를 할 수 있는지: 블로그장, 또는 글 관리 권한을 받은 부블로그장. */
    public boolean canManagePosts() {
        return isActive() && (role == BlogRole.OWNER || (role == BlogRole.SUB_OWNER && canManagePosts));
    }

    /** 블로그 정보를 고칠 수 있는지: 블로그장, 또는 정보 수정 권한을 받은 부블로그장. */
    public boolean canEditInfo() {
        return isActive() && (role == BlogRole.OWNER || (role == BlogRole.SUB_OWNER && canEditInfo));
    }

    public LocalDateTime getSuspendedUntil() {
        return suspendedUntil;
    }

    public LocalDateTime getSubOwnerSince() {
        return subOwnerSince;
    }

    public LocalDateTime getLeftAt() {
        return leftAt;
    }
}
