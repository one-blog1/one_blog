package com.oneblog.auth;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 이 기기(keepId)를 뺀 회원의 다른 로그인을 모두 끝낸다 (비밀번호 변경, SEC-04). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken t set t.revokedAt = :now
            where t.user.id = :userId and t.revokedAt is null and t.id <> :keepId
            """)
    int revokeOthers(@Param("userId") Long userId, @Param("keepId") Long keepId, @Param("now") LocalDateTime now);

    /** 회원의 로그인을 모두 끝낸다 (비밀번호 재설정 015, 회원탈퇴 012). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.user.id = :userId and t.revokedAt is null")
    int revokeAll(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
