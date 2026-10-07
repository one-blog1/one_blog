package com.oneblog.auth;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.member.User;

/**
 * 요청마다 로그인 행과 회원 상태를 DB에서 확인한다 (constitution III, D-53).
 * 로그아웃(revoked_at)과 30분 무활동(expires_at)이 즉시 반영된다 (USR-04, SEC-04).
 */
@Service
public class SessionService {

    private final RefreshTokenRepository refreshTokenRepository;

    public SessionService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * @param userActivity 사용자가 직접 한 행동으로 보낸 요청이면 true (X-User-Activity: 1, D-62)
     * @return 유효하면 로그인한 회원, 아니면 비어 있음
     */
    @Transactional
    public Optional<User> validate(Long userId, Long sessionId, boolean userActivity) {
        LocalDateTime now = LocalDateTime.now();
        return refreshTokenRepository.findById(sessionId)
                .filter(token -> token.isActive(now))
                .filter(token -> token.getUser().getId().equals(userId))
                .filter(token -> token.getUser().isActive())
                .map(token -> {
                    if (userActivity) {
                        token.touch(now);
                    }
                    return token.getUser();
                });
    }
}
