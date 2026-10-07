package com.oneblog.member;

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

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", length = 20, nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 20, nullable = false)
    private UserStatus status;

    @Column(name = "terms_agreed_at")
    private LocalDateTime termsAgreedAt;

    @Column(name = "privacy_agreed_at")
    private LocalDateTime privacyAgreedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
