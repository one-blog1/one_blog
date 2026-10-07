package com.oneblog.verification;

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
import jakarta.persistence.Table;

/**
 * 이메일 인증번호 (Crowfoot ERD verification_codes). 인증번호 원문은 저장하지 않고 HMAC만 둔다.
 * 상태 전이는 data-model.md 참고.
 */
@Entity
@Table(name = "verification_codes")
public class VerificationCode {

    public static final int MAX_FAILURES = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", length = 255, nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "purpose", length = 20, nullable = false)
    private VerificationPurpose purpose;

    @Column(name = "code_hash", length = 100, nullable = false)
    private String codeHash;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "fail_count", nullable = false)
    private int failCount;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected VerificationCode() {
    }

    public static VerificationCode issue(String email, VerificationPurpose purpose, String codeHash,
            LocalDateTime now, LocalDateTime expiresAt) {
        VerificationCode code = new VerificationCode();
        code.email = email;
        code.purpose = purpose;
        code.codeHash = codeHash;
        code.failCount = 0;
        code.createdAt = now;
        code.expiresAt = expiresAt;
        return code;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isInvalidated() {
        return failCount >= MAX_FAILURES || usedAt != null;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    /** 틀린 번호 입력. 남은 시도 횟수를 돌려준다. */
    public int recordFailure() {
        failCount++;
        return Math.max(0, MAX_FAILURES - failCount);
    }

    public void markVerified(LocalDateTime now) {
        this.verifiedAt = now;
    }

    public void markUsed(LocalDateTime now) {
        this.usedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public VerificationPurpose getPurpose() {
        return purpose;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public int getFailCount() {
        return failCount;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
