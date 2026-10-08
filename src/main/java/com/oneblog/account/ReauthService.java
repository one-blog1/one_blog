package com.oneblog.account;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.auth.AccessTokenService;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.security.RateLimiter;
import com.oneblog.common.web.ApiException;
import com.oneblog.common.web.ErrorResponse;
import com.oneblog.member.User;
import com.oneblog.member.ValidationFailedException;

/**
 * 회원정보 수정 전 비밀번호 재확인 (USR-07, D-102).
 * 확인되면 10분짜리 티켓을 주고, 프로필·비밀번호 수정은 이 로그인(sid)의 티켓이 있어야 한다.
 */
@Service
public class ReauthService {

    static final int ATTEMPTS = 5;
    static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(10);

    private final AccountService accountService;
    private final AccessTokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;

    public ReauthService(AccountService accountService, AccessTokenService tokenService,
            PasswordEncoder passwordEncoder, RateLimiter rateLimiter) {
        this.accountService = accountService;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
    }

    /** 비밀번호가 맞으면 티켓을 돌려준다. 회원당 10분에 5번까지 시도할 수 있다. */
    @Transactional(readOnly = true)
    public String verify(AuthenticatedUser principal, String password) {
        User user = accountService.member(principal);
        rateLimiter.check("reauth", String.valueOf(user.getId()), ATTEMPTS, ATTEMPT_WINDOW);
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ValidationFailedException(List.of(
                    new ErrorResponse.FieldError("password", "비밀번호가 맞지 않습니다.")));
        }
        return tokenService.issueReauthTicket(principal.id(), principal.sessionId());
    }

    public Optional<Instant> expiresAt(AuthenticatedUser principal, String ticket) {
        return tokenService.readReauthTicket(ticket, principal.id(), principal.sessionId());
    }

    /** 티켓이 없거나 만료됐거나 다른 로그인의 것이면 403 REAUTH_REQUIRED. */
    public void require(AuthenticatedUser principal, String ticket) {
        if (expiresAt(principal, ticket).isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REAUTH_REQUIRED", "비밀번호를 다시 확인해 주세요.");
        }
    }
}
