package com.oneblog.auth;

import java.time.Duration;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.oneblog.member.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 로그인 유지 정보 (Crowfoot ERD refresh_tokens). 기기마다 한 행이다.
 * expires_at이 만료의 단일 기준이다: 로그인 유지 체크 시 로그인 + 14일 고정, 미체크 시 마지막 활동 + 30분 (research R3).
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    public static final Duration REMEMBER_ME_TTL = Duration.ofDays(14);
    public static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);
    /** 활동 시각을 이 간격보다 자주 쓰지 않는다. */
    public static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", length = 64, nullable = false)
    private String tokenHash;

    @Column(name = "remember_me", nullable = false)
    private boolean rememberMe;

    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RefreshToken() {
    }

    public static RefreshToken issue(User user, String tokenHash, boolean rememberMe, LocalDateTime now) {
        RefreshToken token = new RefreshToken();
        token.user = user;
        token.tokenHash = tokenHash;
        token.rememberMe = rememberMe;
        token.createdAt = now;
        token.lastActivityAt = now;
        token.expiresAt = rememberMe ? now.plus(REMEMBER_ME_TTL) : now.plus(IDLE_TIMEOUT);
        return token;
    }

    public boolean isActive(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    /**
     * 사용자가 직접 한 행동이 있을 때 부른다. 로그인 유지 미체크이고 마지막 갱신이 1분 이상 지났을 때만 30분을 다시 시작한다.
     * @return 값을 바꿨으면 true
     */
    public boolean touch(LocalDateTime now) {
        if (rememberMe || lastActivityAt.plus(TOUCH_INTERVAL).isAfter(now)) {
            return false;
        }
        this.lastActivityAt = now;
        this.expiresAt = now.plus(IDLE_TIMEOUT);
        return true;
    }

    public void revoke(LocalDateTime now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public boolean isRememberMe() {
        return rememberMe;
    }

    public LocalDateTime getLastActivityAt() {
        return lastActivityAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }
}
