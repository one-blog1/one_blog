package com.oneblog.member;

import java.time.Duration;
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
 * 회원 (Crowfoot ERD users). 이 기능에서 쓰는 컬럼만 매핑하고, 나머지는 DB 기본값을 쓴다.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "login_id", length = 30)
    private String loginId;

    @Column(name = "password_hash", length = 100, nullable = false)
    private String passwordHash;

    @Column(name = "name", length = 50)
    private String name;

    @Column(name = "nickname", length = 12)
    private String nickname;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "bio", length = 300)
    private String bio;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", length = 20, nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 20, nullable = false)
    private UserStatus status;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /** 알림 보관 일수 30 또는 7 (4.5, 6.7). */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "notification_retention_days", nullable = false)
    private int notificationRetentionDays = 30;

    /** 프로필 공개 범위 (SOC-03, D-114). */
    @Column(name = "show_blogs_on_profile", nullable = false)
    private boolean showBlogsOnProfile = true;

    @Column(name = "show_follows_on_profile", nullable = false)
    private boolean showFollowsOnProfile = true;

    @Column(name = "allow_follow", nullable = false)
    private boolean allowFollow = true;

    @Column(name = "terms_agreed_at")
    private LocalDateTime termsAgreedAt;

    @Column(name = "privacy_agreed_at")
    private LocalDateTime privacyAgreedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected User() {
    }

    /** 일반 회원 가입 (USR-01). 입력값은 SignupPolicy로 정리·검증된 값이어야 한다. */
    public static User createMember(String email, String passwordHash, String name, String nickname,
            String phone, LocalDateTime agreedAt) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.name = name;
        user.nickname = nickname;
        user.phone = phone;
        user.role = UserRole.USER;
        user.status = UserStatus.ACTIVE;
        user.termsAgreedAt = agreedAt;
        user.privacyAgreedAt = agreedAt;
        return user;
    }

    /**
     * 관리자 계정 (SEC-09, D-94, D-98). 회원가입 화면으로는 만들 수 없고 운영자가 초기 데이터로 만든다.
     * 이메일 없이 관리자 전용 아이디(login_id)로 로그인한다.
     */
    public static User createAdmin(String loginId, String passwordHash) {
        User user = new User();
        user.loginId = loginId;
        user.passwordHash = passwordHash;
        user.role = UserRole.ADMIN;
        user.status = UserStatus.ACTIVE;
        return user;
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

    /** 회원정보 수정 (USR-07). 이메일·이름은 바꿀 수 없다. 값은 AccountService가 정리·검사한 값. */
    public void changeProfile(String nickname, String phone, String bio) {
        this.nickname = nickname;
        this.phone = phone;
        this.bio = bio;
    }

    public void changeProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * 회원탈퇴 (USR-05, 4.5). 로그인용 이메일은 바로 비워 같은 이메일로 다시 가입할 수 있게 하고(6.5),
     * 나머지 개인정보는 30일 뒤 배치가 지운다 (purgePersonalInfo).
     */
    public void withdraw(LocalDateTime now) {
        this.status = UserStatus.WITHDRAWN;
        this.email = null;
        this.withdrawnAt = now;
    }

    /** 탈퇴 30일 뒤 개인정보 삭제 (4.5). 쓴 글은 "탈퇴한 회원"으로 남는다. */
    public void purgePersonalInfo(LocalDateTime now) {
        this.name = null;
        this.nickname = null;
        this.phone = null;
        this.profileImageUrl = null;
        this.bio = null;
        this.deletedAt = now;
    }

    public LocalDateTime getWithdrawnAt() {
        return withdrawnAt;
    }

    /**
     * 로그인 실패 기록 (SEC-03, 6.7). 5번 연속 틀리면 5분 잠그고 횟수를 0으로 되돌린다.
     * @return 잠겼으면 true
     */
    public boolean recordLoginFailure(LocalDateTime now, int maxFailures, Duration lockFor) {
        this.failedLoginCount++;
        if (failedLoginCount >= maxFailures) {
            this.lockedUntil = now.plus(lockFor);
            this.failedLoginCount = 0;
            return true;
        }
        return false;
    }

    /** 로그인 성공·비밀번호 재설정 때 실패 횟수와 잠금을 푼다 (USR-06). */
    public void clearLoginFailures() {
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }

    public boolean isLocked(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void changeNotificationRetentionDays(int days) {
        this.notificationRetentionDays = days;
    }

    public int getNotificationRetentionDays() {
        return notificationRetentionDays;
    }

    public void changePrivacy(boolean showBlogs, boolean showFollows, boolean allowFollow) {
        this.showBlogsOnProfile = showBlogs;
        this.showFollowsOnProfile = showFollows;
        this.allowFollow = allowFollow;
    }

    public boolean isShowBlogsOnProfile() {
        return showBlogsOnProfile;
    }

    public boolean isShowFollowsOnProfile() {
        return showFollowsOnProfile;
    }

    public boolean isAllowFollow() {
        return allowFollow;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public String getBio() {
        return bio;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE && deletedAt == null;
    }

    public Long getId() {
        return id;
    }

    public String getLoginId() {
        return loginId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public String getNickname() {
        return nickname;
    }

    public String getPhone() {
        return phone;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public LocalDateTime getTermsAgreedAt() {
        return termsAgreedAt;
    }

    public LocalDateTime getPrivacyAgreedAt() {
        return privacyAgreedAt;
    }
}
