package com.oneblog.account;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/** 비밀번호 찾기·재설정(USR-06), 이메일 찾기(USR-08). 로그인하지 않은 사람이 쓴다 (POST /api/auth/**). */
@RestController
public class AccountRecoveryController {

    static final String SENT_MESSAGE = "입력한 이메일로 안내를 보냈습니다.";

    private final PasswordResetService passwordResetService;
    private final EmailFindService emailFindService;

    public AccountRecoveryController(PasswordResetService passwordResetService, EmailFindService emailFindService) {
        this.passwordResetService = passwordResetService;
        this.emailFindService = emailFindService;
    }

    public record EmailRequest(String email) {
    }

    public record TokenRequest(String token) {
    }

    @PostMapping("/api/auth/password-reset/code")
    public ResponseEntity<Map<String, String>> requestCode(@RequestBody EmailRequest request, HttpServletRequest http) {
        passwordResetService.requestByEmail(request.email(), http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("message", SENT_MESSAGE));
    }

    @PostMapping("/api/auth/password-reset")
    public ResponseEntity<Void> reset(@RequestBody PasswordResetService.ResetRequest request) {
        passwordResetService.reset(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/auth/find-email")
    public List<EmailFindService.FoundAccount> findEmail(@RequestBody EmailFindService.FindRequest request,
            HttpServletRequest http) {
        return emailFindService.find(request, http.getRemoteAddr());
    }

    @PostMapping("/api/auth/find-email/reset-code")
    public ResponseEntity<Map<String, String>> resetCodeFromLookup(@RequestBody TokenRequest request,
            HttpServletRequest http) {
        passwordResetService.requestByLookupToken(request.token(), http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("message", "가입한 이메일로 인증번호를 보냈습니다."));
    }
}
