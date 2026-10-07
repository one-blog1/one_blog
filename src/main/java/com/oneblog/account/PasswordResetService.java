package com.oneblog.account;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.auth.RefreshTokenRepository;
import com.oneblog.common.security.RateLimiter;
import com.oneblog.common.security.TokenHasher;
import com.oneblog.common.web.ApiException;
import com.oneblog.common.web.ErrorResponse;
import com.oneblog.mail.MailService;
import com.oneblog.member.SignupPolicy;
import com.oneblog.member.User;
import com.oneblog.member.UserRepository;
import com.oneblog.member.UserRole;
import com.oneblog.member.ValidationFailedException;
import com.oneblog.verification.VerificationCode;
import com.oneblog.verification.VerificationCodeRepository;
import com.oneblog.verification.VerificationPurpose;

/**
 * 비밀번호 찾기·재설정 (USR-06, SEC-05, 6.7)과 이메일 찾기에서 이어지는 재설정 (USR-08, D-26).
 * - 인증번호 6자리, 30분 만료, 한 번만, 5번 틀리면 무효. 다시 받으면 이전 번호는 무효(1분에 한 번)
 * - 화면은 가입 여부와 관계없이 같은 문구 ("입력한 이메일로 안내를 보냈습니다")
 * - 바꾸면 모든 기기의 로그인이 끝나고 로그인 잠금도 풀린다
 * - 같은 IP는 10분에 5번까지 요청한다 (D-80)
 */
@Service
public class PasswordResetService {

    public static final Duration CODE_TTL = Duration.ofMinutes(30);
    public static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
    static final int REQUESTS_PER_IP = 5;
    static final Duration REQUEST_WINDOW = Duration.ofMinutes(10);
    private static final String EXPIRED = "인증번호가 만료되었거나 맞지 않습니다. 다시 받아 주세요.";

    private final UserRepository userRepository;
    private final VerificationCodeRepository codeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHasher tokenHasher;
    private final MailService mailService;
    private final SignupPolicy policy;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final NamedParameterJdbcTemplate jdbc;

    public PasswordResetService(UserRepository userRepository, VerificationCodeRepository codeRepository,
            RefreshTokenRepository refreshTokenRepository, TokenHasher tokenHasher, MailService mailService,
            SignupPolicy policy, PasswordEncoder passwordEncoder, RateLimiter rateLimiter,
            NamedParameterJdbcTemplate jdbc) {
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenHasher = tokenHasher;
        this.mailService = mailService;
        this.policy = policy;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.jdbc = jdbc;
    }

    public record ResetRequest(String email, String lookupToken, String code, String newPassword,
            String newPasswordConfirm) {

        @Override
        public String toString() {
            return "ResetRequest[email=" + email + "]";
        }
    }

    /** 이메일로 인증번호를 보낸다. 가입 여부와 관계없이 같은 결과 (USR-06). */
    @Transactional
    public void requestByEmail(String rawEmail, String ip) {
        rateLimiter.check("password-reset", String.valueOf(ip), REQUESTS_PER_IP, REQUEST_WINDOW);
        String email = policy.normalizeEmail(rawEmail);
        if (email == null || email.isBlank()) {
            throw new ValidationFailedException(List.of(new ErrorResponse.FieldError("email", "이메일을 입력해 주세요.")));
        }
        userRepository.findByEmail(email).filter(u -> u.isActive() && u.getRole() == UserRole.USER)
                .ifPresent(u -> sendCode(u.getEmail()));
    }

    /** 이메일 찾기 결과의 [비밀번호 재설정] (USR-08 ③). 회원 번호 대신 10분짜리 임시 토큰으로 요청한다. */
    @Transactional
    public void requestByLookupToken(String token, String ip) {
        rateLimiter.check("password-reset", String.valueOf(ip), REQUESTS_PER_IP, REQUEST_WINDOW);
        User user = userOfLookupToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.GONE, "LOOKUP_EXPIRED", "시간이 지났습니다. 이메일 찾기를 다시 해 주세요."));
        sendCode(user.getEmail());
    }

    private void sendCode(String email) {
        LocalDateTime now = LocalDateTime.now();
        Optional<VerificationCode> latest = codeRepository
                .findTopByEmailAndPurposeOrderByCreatedAtDescIdDesc(email, VerificationPurpose.PASSWORD_RESET);
        if (latest.isPresent() && latest.get().getCreatedAt().plus(RESEND_INTERVAL).isAfter(now)) {
            // 화면 문구는 같게 두고 메일만 보내지 않는다 (가입 여부를 드러내지 않기 위해 오류를 내지 않음)
            return;
        }
        codeRepository.deleteAllByEmailAndPurpose(email, VerificationPurpose.PASSWORD_RESET);
        String code = tokenHasher.randomSixDigits();
        codeRepository.save(VerificationCode.issue(email, VerificationPurpose.PASSWORD_RESET, tokenHasher.hmac(code), now,
                now.plus(CODE_TTL)));
        mailService.sendPasswordResetCode(email, code);
    }

    /**
     * 인증번호를 확인하고 새 비밀번호로 바꾼다 (USR-06 ④, USR-08 ④).
     * noRollbackFor: 틀린 횟수는 오류 응답을 주더라도 저장해야 한다.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void reset(ResetRequest request) {
        User user;
        if (request.lookupToken() != null && !request.lookupToken().isBlank()) {
            user = userOfLookupToken(request.lookupToken()).orElseThrow(() -> new ApiException(HttpStatus.GONE,
                    "LOOKUP_EXPIRED", "시간이 지났습니다. 이메일 찾기를 다시 해 주세요."));
        } else {
            String email = policy.normalizeEmail(request.email());
            user = email == null ? null : userRepository.findByEmail(email)
                    .filter(u -> u.isActive() && u.getRole() == UserRole.USER).orElse(null);
        }
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        if (!policy.isValidPassword(request.newPassword())) {
            errors.add(new ErrorResponse.FieldError("newPassword", "비밀번호는 8~15자이며 영문, 숫자, 특수문자를 모두 포함해야 합니다."));
        } else if (!request.newPassword().equals(request.newPasswordConfirm())) {
            errors.add(new ErrorResponse.FieldError("newPasswordConfirm", "비밀번호가 서로 다릅니다."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        if (user == null) {
            throw new ApiException(HttpStatus.GONE, "CODE_EXPIRED", EXPIRED);
        }
        LocalDateTime now = LocalDateTime.now();
        VerificationCode verification = codeRepository
                .findTopByEmailAndPurposeOrderByCreatedAtDescIdDesc(user.getEmail(), VerificationPurpose.PASSWORD_RESET)
                .filter(v -> !v.isExpired(now) && !v.isInvalidated() && !v.isUsed())
                .orElseThrow(() -> new ApiException(HttpStatus.GONE, "CODE_EXPIRED", EXPIRED));
        if (request.code() == null || !tokenHasher.matches(verification.getCodeHash(), tokenHasher.hmac(request.code()))) {
            int remaining = verification.recordFailure();
            if (remaining == 0) {
                throw new ApiException(HttpStatus.GONE, "CODE_EXPIRED", "인증번호를 5번 틀려 무효가 되었습니다. 다시 받아 주세요.");
            }
            throw ApiException.withRemainingAttempts(HttpStatus.BAD_REQUEST, "CODE_MISMATCH", "인증번호가 올바르지 않습니다.",
                    remaining);
        }
        // 한 번만 쓸 수 있다 (SEC-05): 쓴 번호는 바로 지운다 (4.5)
        codeRepository.deleteAllByEmailAndPurpose(user.getEmail(), VerificationPurpose.PASSWORD_RESET);
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        user.clearLoginFailures();
        userRepository.saveAndFlush(user);
        refreshTokenRepository.revokeAll(user.getId(), now);
        jdbc.update("UPDATE account_lookup_tokens SET used_at = :now WHERE user_id = :userId AND used_at IS NULL",
                new MapSqlParameterSource().addValue("now", now).addValue("userId", user.getId()));
    }

    /** 쓰지 않았고 만료되지 않은 임시 토큰의 회원. */
    Optional<User> userOfLookupToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        List<Long> ids = jdbc.queryForList("""
                SELECT user_id FROM account_lookup_tokens
                WHERE token_hash = :hash AND used_at IS NULL AND expires_at > :now
                """, new MapSqlParameterSource().addValue("hash", tokenHasher.sha256(token))
                .addValue("now", LocalDateTime.now()), Long.class);
        return ids.isEmpty() ? Optional.empty()
                : userRepository.findById(ids.get(0)).filter(u -> u.isActive() && u.getRole() == UserRole.USER);
    }
}
