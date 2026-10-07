package com.oneblog.member;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.oneblog.common.security.AuthCookies;
import com.oneblog.common.security.CookieNames;
import com.oneblog.member.dto.NicknameAvailabilityResponse;
import com.oneblog.member.dto.SignupRequest;
import com.oneblog.verification.EmailVerificationService;
import com.oneblog.verification.dto.EmailCodeRequest;
import com.oneblog.verification.dto.VerifyCodeRequest;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** 회원가입 API (USR-01, USR-02). 형식은 contracts/auth-api.md. */
@RestController
@RequestMapping("/api/auth/signup")
public class SignupController {

    private final EmailVerificationService verificationService;
    private final SignupService signupService;
    private final NicknameService nicknameService;
    private final AuthCookies authCookies;

    public SignupController(EmailVerificationService verificationService, SignupService signupService,
            NicknameService nicknameService, AuthCookies authCookies) {
        this.verificationService = verificationService;
        this.signupService = signupService;
        this.nicknameService = nicknameService;
        this.authCookies = authCookies;
    }

    /** 인증번호 받기. 가입 여부와 관계없이 같은 응답 (D-28). */
    @PostMapping("/email-code")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> sendCode(@Valid @RequestBody EmailCodeRequest request) {
        verificationService.sendSignupCode(request.email());
        return Map.of(
                "message", "인증번호를 보냈습니다.",
                "expiresInSeconds", EmailVerificationService.CODE_TTL.toSeconds(),
                "resendAvailableInSeconds", EmailVerificationService.RESEND_INTERVAL.toSeconds());
    }

    /** 인증번호 확인. 맞으면 30분짜리 가입 티켓 쿠키를 준다. */
    @PostMapping("/email-code/verify")
    public Map<String, Object> verifyCode(@Valid @RequestBody VerifyCodeRequest request,
            HttpServletResponse response) {
        String ticket = verificationService.verifySignupCode(request.email(), request.code());
        authCookies.setSignupTicket(response, ticket);
        return Map.of(
                "verified", true,
                "signupExpiresInSeconds", AuthCookies.SIGNUP_TICKET_TTL.toSeconds());
    }

    @GetMapping("/nickname-availability")
    public NicknameAvailabilityResponse nicknameAvailability(@RequestParam("nickname") String nickname) {
        return nicknameService.check(nickname);
    }

    /** 가입 완료. 이메일은 가입 티켓에서 읽는다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> signup(
            @CookieValue(name = CookieNames.SIGNUP_TICKET, required = false) String ticket,
            @Valid @RequestBody SignupRequest request, HttpServletResponse response) {
        signupService.signup(ticket, request);
        authCookies.clearSignupTicket(response);
        return Map.of("message", "가입이 완료되었습니다. 로그인해 주세요.");
    }
}
