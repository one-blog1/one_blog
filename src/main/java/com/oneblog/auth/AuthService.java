package com.oneblog.auth;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.common.security.TokenHasher;
import com.oneblog.common.web.ApiException;
import com.oneblog.member.SignupPolicy;
import com.oneblog.member.User;
import com.oneblog.member.UserRepository;
import com.oneblog.member.UserRole;

/**
 * 로그인·Access Token 재발급·로그아웃 (USR-03, USR-04, SEC-04, research R3).
 */
@Service
public class AuthService {

    private static final String LOGIN_FAILED_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다.";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenHasher tokenHasher;
    private final AccessTokenService accessTokenService;
    private final SignupPolicy signupPolicy;
    /** 없는 이메일에도 같은 시간만큼 bcrypt 비교를 해서 응답 시간으로 가입 여부를 알 수 없게 한다. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder, TokenHasher tokenHasher, AccessTokenService accessTokenService,
            SignupPolicy signupPolicy) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenHasher = tokenHasher;
        this.accessTokenService = accessTokenService;
        this.signupPolicy = signupPolicy;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing!1");
    }

    @Transactional
    public LoginResult login(String rawEmail, String password, boolean rememberMe) {
        String email = signupPolicy.normalizeEmail(rawEmail);
        User user = userRepository.findByEmail(email).orElse(null);
        boolean passwordMatches = passwordEncoder.matches(password, user != null ? user.getPasswordHash() : dummyHash);
        // 관리자 계정은 회원 로그인 화면으로 들어올 수 없다 (SEC-09, D-98)
        if (user == null || !passwordMatches || !user.isActive() || user.getRole() == UserRole.ADMIN) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", LOGIN_FAILED_MESSAGE);
        }
        return issue(user, rememberMe);
    }

    /** 관리자 로그인: 이메일 대신 관리자 전용 아이디(login_id) (SEC-09, D-98). 회원 계정은 들어올 수 없다. */
    @Transactional
    public LoginResult adminLogin(String rawLoginId, String password, boolean rememberMe) {
        String loginId = rawLoginId == null ? "" : rawLoginId.strip().toLowerCase(java.util.Locale.ROOT);
        User user = userRepository.findByLoginId(loginId).orElse(null);
        boolean passwordMatches = passwordEncoder.matches(password, user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !passwordMatches || !user.isActive() || user.getRole() != UserRole.ADMIN) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        return issue(user, rememberMe);
    }

    private LoginResult issue(User user, boolean rememberMe) {
        String rawRefreshToken = tokenHasher.randomToken();
        RefreshToken session = refreshTokenRepository.save(
                RefreshToken.issue(user, tokenHasher.sha256(rawRefreshToken), rememberMe, LocalDateTime.now()));
        String accessToken = accessTokenService.issueAccessToken(user.getId(), session.getId());
        return new LoginResult(accessToken, rawRefreshToken, rememberMe, user.getNickname());
    }

    /** Refresh Token으로 Access Token을 다시 받는다. Refresh Token 교체는 1차에 하지 않는다. */
    @Transactional(readOnly = true)
    public RefreshResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw sessionExpired();
        }
        LocalDateTime now = LocalDateTime.now();
        RefreshToken session = refreshTokenRepository.findByTokenHash(tokenHasher.sha256(rawRefreshToken))
                .filter(token -> token.isActive(now))
                .filter(token -> token.getUser().isActive())
                .orElseThrow(AuthService::sessionExpired);
        String accessToken = accessTokenService.issueAccessToken(session.getUser().getId(), session.getId());
        return new RefreshResult(accessToken, session.isRememberMe());
    }

    /** 그 기기의 로그인을 끊는다. 이미 로그아웃된 상태여도 오류를 내지 않는다 (USR-04). */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(tokenHasher.sha256(rawRefreshToken))
                .ifPresent(token -> token.revoke(LocalDateTime.now()));
    }

    @Transactional(readOnly = true)
    public User getActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "로그인이 필요합니다."));
    }

    private static ApiException sessionExpired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "로그인이 만료되었습니다. 다시 로그인해 주세요.");
    }

    public record LoginResult(String accessToken, String refreshToken, boolean rememberMe, String nickname) {
    }

    public record RefreshResult(String accessToken, boolean rememberMe) {
    }
}
