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
    /** 6.7: 5번 연속 틀리면 5분 잠금, 3번 틀린 뒤부터 사람 확인. */
    public static final int MAX_LOGIN_FAILURES = 5;
    public static final java.time.Duration LOCK_DURATION = java.time.Duration.ofMinutes(5);
    public static final int CAPTCHA_AFTER_FAILURES = 3;
    /** D-80: 같은 IP는 10분에 로그인 30번까지 (여러 계정을 돌려 가며 시도하는 매크로 방지). */
    static final int LOGIN_PER_IP = 30;
    static final java.time.Duration LOGIN_WINDOW = java.time.Duration.ofMinutes(10);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenHasher tokenHasher;
    private final AccessTokenService accessTokenService;
    private final SignupPolicy signupPolicy;
    private final com.oneblog.common.security.RateLimiter rateLimiter;
    private final com.oneblog.common.security.TurnstileVerifier turnstile;
    /** 없는 이메일에도 같은 시간만큼 bcrypt 비교를 해서 응답 시간으로 가입 여부를 알 수 없게 한다. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder, TokenHasher tokenHasher, AccessTokenService accessTokenService,
            SignupPolicy signupPolicy, com.oneblog.common.security.RateLimiter rateLimiter,
            com.oneblog.common.security.TurnstileVerifier turnstile) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenHasher = tokenHasher;
        this.accessTokenService = accessTokenService;
        this.signupPolicy = signupPolicy;
        this.rateLimiter = rateLimiter;
        this.turnstile = turnstile;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing!1");
    }

    /**
     * 회원 로그인 (USR-03). 계정별 잠금(5번 틀리면 5분, SEC-03) + IP별 요청 제한(D-80) + 3번 틀린 뒤 사람 확인(SEC-13).
     * noRollbackFor: 실패 횟수는 오류 응답을 주더라도 저장해야 한다.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult login(String rawEmail, String password, boolean rememberMe, String ip, String captchaToken) {
        rateLimiter.check("login", String.valueOf(ip), LOGIN_PER_IP, LOGIN_WINDOW);
        String email = signupPolicy.normalizeEmail(rawEmail);
        User user = userRepository.findByEmail(email).orElse(null);
        // 관리자 계정은 회원 로그인 화면으로 들어올 수 없다 (SEC-09, D-98)
        authenticate(user != null && user.getRole() == UserRole.ADMIN ? null : user, password, ip, captchaToken,
                LOGIN_FAILED_MESSAGE);
        return issue(user, rememberMe);
    }

    /** 관리자 로그인: 이메일 대신 관리자 전용 아이디(login_id) (SEC-09, D-98). 회원 계정은 들어올 수 없다. */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult adminLogin(String rawLoginId, String password, boolean rememberMe, String ip,
            String captchaToken) {
        rateLimiter.check("login", String.valueOf(ip), LOGIN_PER_IP, LOGIN_WINDOW);
        String loginId = rawLoginId == null ? "" : rawLoginId.strip().toLowerCase(java.util.Locale.ROOT);
        User user = userRepository.findByLoginId(loginId).orElse(null);
        // 관리자는 계정 잠금·사람 확인을 하지 않는다 (D-105). 같은 IP 요청 제한(D-80)만 남는다
        authenticate(user != null && user.getRole() == UserRole.ADMIN ? user : null, password, ip, captchaToken,
                "아이디 또는 비밀번호가 올바르지 않습니다.", false);
        return issue(user, rememberMe);
    }

    /** 사용자가 null이어도 같은 시간만큼 bcrypt를 돌려 응답 시간으로 가입 여부를 알 수 없게 한다. */
    private void authenticate(User user, String password, String ip, String captchaToken, String failMessage) {
        authenticate(user, password, ip, captchaToken, failMessage, true);
    }

    /** lockable=false면 실패 횟수·잠금·사람 확인을 건너뛴다 (관리자, D-105). */
    private void authenticate(User user, String password, String ip, String captchaToken, String failMessage,
            boolean lockable) {
        LocalDateTime now = LocalDateTime.now();
        if (lockable && user != null && user.isLocked(now)) {
            long seconds = Math.max(1, java.time.Duration.between(now, user.getLockedUntil()).toSeconds());
            throw ApiException.withRetryAfter(HttpStatus.LOCKED, "LOGIN_LOCKED",
                    "로그인을 5번 틀려 잠시 잠겼습니다. 잠시 후 다시 시도해 주세요.", seconds);
        }
        if (lockable && user != null && user.getFailedLoginCount() >= CAPTCHA_AFTER_FAILURES
                && !turnstile.verify(captchaToken, ip)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CAPTCHA_REQUIRED", "사람인지 확인해 주세요.");
        }
        boolean passwordMatches = passwordEncoder.matches(password == null ? "" : password,
                user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !user.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", failMessage);
        }
        if (!passwordMatches && !lockable) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", failMessage);
        }
        if (!passwordMatches) {
            boolean locked = user.recordLoginFailure(now, MAX_LOGIN_FAILURES, LOCK_DURATION);
            userRepository.saveAndFlush(user);
            if (locked) {
                throw ApiException.withRetryAfter(HttpStatus.LOCKED, "LOGIN_LOCKED",
                        "로그인을 5번 틀려 5분 동안 잠겼습니다.", LOCK_DURATION.toSeconds());
            }
            if (user.getFailedLoginCount() >= CAPTCHA_AFTER_FAILURES && turnstile.enabled()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED_CAPTCHA", failMessage);
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", failMessage);
        }
        if (user.getFailedLoginCount() > 0 || user.getLockedUntil() != null) {
            user.clearLoginFailures();
            userRepository.saveAndFlush(user);
        }
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
