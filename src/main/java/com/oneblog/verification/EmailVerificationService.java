package com.oneblog.verification;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.auth.AccessTokenService;
import com.oneblog.common.security.TokenHasher;
import com.oneblog.common.web.ApiException;
import com.oneblog.mail.MailService;
import com.oneblog.member.SignupPolicy;
import com.oneblog.member.UserRepository;

/**
 * 가입 이메일 인증 (USR-02, D-28, research R5).
 * - 인증번호 6자리, 10분 만료, 5번 틀리면 무효, 다시 보내기 1분에 한 번, 다시 보내면 이전 번호 무효
 * - 가입된 이메일도 같은 응답을 주고, 인증번호 대신 안내 메일을 보낸다
 */
@Service
public class EmailVerificationService {

    public static final Duration CODE_TTL = Duration.ofMinutes(10);
    public static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
    private static final String EXPIRED_MESSAGE = "인증번호가 만료되었습니다. 다시 받아 주세요.";

    private final VerificationCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final TokenHasher tokenHasher;
    private final MailService mailService;
    private final SignupPolicy signupPolicy;
    private final AccessTokenService accessTokenService;

    public EmailVerificationService(VerificationCodeRepository codeRepository, UserRepository userRepository,
            TokenHasher tokenHasher, MailService mailService, SignupPolicy signupPolicy,
            AccessTokenService accessTokenService) {
        this.codeRepository = codeRepository;
        this.userRepository = userRepository;
        this.tokenHasher = tokenHasher;
        this.mailService = mailService;
        this.signupPolicy = signupPolicy;
        this.accessTokenService = accessTokenService;
    }

    /** 인증번호를 보낸다. 가입 여부와 관계없이 같은 결과를 돌려준다 (D-28). */
    @Transactional
    public void sendSignupCode(String rawEmail) {
        String email = signupPolicy.normalizeEmail(rawEmail);
        LocalDateTime now = LocalDateTime.now();

        Optional<VerificationCode> latest = codeRepository
                .findTopByEmailAndPurposeOrderByCreatedAtDescIdDesc(email, VerificationPurpose.SIGNUP);
        if (latest.isPresent()) {
            LocalDateTime availableAt = latest.get().getCreatedAt().plus(RESEND_INTERVAL);
            if (availableAt.isAfter(now)) {
                long seconds = Math.max(1, Duration.between(now, availableAt).toSeconds());
                throw ApiException.withRetryAfter(HttpStatus.TOO_MANY_REQUESTS, "RESEND_TOO_SOON",
                        "잠시 후 다시 보낼 수 있습니다.", seconds);
            }
        }

        // 다시 보내면 이전 번호는 무효 (행 삭제)
        codeRepository.deleteAllByEmailAndPurpose(email, VerificationPurpose.SIGNUP);
        String code = tokenHasher.randomSixDigits();
        codeRepository.save(VerificationCode.issue(email, VerificationPurpose.SIGNUP,
                tokenHasher.hmac(code), now, now.plus(CODE_TTL)));

        if (userRepository.existsByEmail(email)) {
            mailService.sendAlreadyRegistered(email);
        } else {
            mailService.sendSignupCode(email, code);
        }
    }

    /**
     * 인증번호를 확인하고, 맞으면 30분짜리 가입 티켓(JWT)을 돌려준다.
     * noRollbackFor: 틀린 횟수(fail_count)는 오류 응답을 주더라도 저장해야 한다.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public String verifySignupCode(String rawEmail, String code) {
        String email = signupPolicy.normalizeEmail(rawEmail);
        LocalDateTime now = LocalDateTime.now();

        VerificationCode verification = codeRepository
                .findTopByEmailAndPurposeOrderByCreatedAtDescIdDesc(email, VerificationPurpose.SIGNUP)
                .orElseThrow(() -> new ApiException(HttpStatus.GONE, "CODE_EXPIRED", EXPIRED_MESSAGE));

        if (verification.isExpired(now) || verification.isInvalidated()) {
            throw new ApiException(HttpStatus.GONE, "CODE_EXPIRED", EXPIRED_MESSAGE);
        }

        if (!tokenHasher.matches(verification.getCodeHash(), tokenHasher.hmac(code))) {
            int remaining = verification.recordFailure();
            if (remaining == 0) {
                throw new ApiException(HttpStatus.GONE, "CODE_EXPIRED",
                        "인증번호를 5번 틀려 무효가 되었습니다. 다시 받아 주세요.");
            }
            throw ApiException.withRemainingAttempts(HttpStatus.BAD_REQUEST, "CODE_MISMATCH",
                    "인증번호가 올바르지 않습니다.", remaining);
        }

        verification.markVerified(now);
        return accessTokenService.issueSignupTicket(email, verification.getId());
    }
}
