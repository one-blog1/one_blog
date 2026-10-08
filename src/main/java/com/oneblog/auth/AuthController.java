package com.oneblog.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.oneblog.auth.dto.LoginRequest;
import com.oneblog.auth.dto.LoginResponse;
import com.oneblog.auth.dto.MeResponse;
import com.oneblog.common.security.AuthCookies;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.security.CookieNames;
import com.oneblog.common.web.ApiException;
import com.oneblog.member.User;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** 로그인·refresh·로그아웃·내 정보 API (USR-03, USR-04). 형식은 contracts/auth-api.md. */
@RestController
public class AuthController {

    private final AuthService authService;
    private final AuthCookies authCookies;
    private final com.oneblog.common.security.TurnstileVerifier turnstile;

    public AuthController(AuthService authService, AuthCookies authCookies,
            com.oneblog.common.security.TurnstileVerifier turnstile) {
        this.authService = authService;
        this.authCookies = authCookies;
        this.turnstile = turnstile;
    }

    @PostMapping("/api/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        AuthService.LoginResult result = authService.login(request.email(), request.password(),
                request.rememberMeOrFalse(), httpRequest.getRemoteAddr(), request.captchaToken());
        authCookies.setLoginCookies(response, result.accessToken(), result.refreshToken(), result.rememberMe());
        return new LoginResponse(result.nickname());
    }

    /** 관리자 로그인 (SEC-09, D-98). 쿠키는 회원 로그인과 같다. */
    @PostMapping("/api/auth/admin/login")
    public LoginResponse adminLogin(@RequestBody AdminLoginRequest request, HttpServletResponse response,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        AuthService.LoginResult result = authService.adminLogin(request.loginId(), request.password(),
                Boolean.TRUE.equals(request.rememberMe()), httpRequest.getRemoteAddr(), request.captchaToken());
        authCookies.setLoginCookies(response, result.accessToken(), result.refreshToken(), result.rememberMe());
        return new LoginResponse(result.nickname());
    }

    public record AdminLoginRequest(String loginId, String password, Boolean rememberMe, String captchaToken) {

        @Override
        public String toString() {
            return "AdminLoginRequest[loginId=" + loginId + "]";
        }
    }

    @PostMapping("/api/auth/refresh")
    public ResponseEntity<Void> refresh(
            @CookieValue(name = CookieNames.REFRESH_TOKEN, required = false) String refreshToken,
            HttpServletResponse response) {
        try {
            AuthService.RefreshResult result = authService.refresh(refreshToken);
            authCookies.setAccessCookie(response, result.accessToken(), result.rememberMe());
            return ResponseEntity.noContent().build();
        } catch (ApiException e) {
            authCookies.clearLoginCookies(response);
            throw e;
        }
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = CookieNames.REFRESH_TOKEN, required = false) String refreshToken,
            HttpServletResponse response) {
        authService.logout(refreshToken);
        authCookies.clearLoginCookies(response);
        return ResponseEntity.noContent().build();
    }

    /** 사람 확인 설정 (SEC-13). 꺼져 있으면 enabled=false. */
    @GetMapping("/api/auth/captcha-config")
    public java.util.Map<String, Object> captchaConfig() {
        java.util.Map<String, Object> config = new java.util.HashMap<>();
        config.put("enabled", turnstile.enabled());
        config.put("siteKey", turnstile.siteKey());
        return config;
    }

    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        User user = authService.getActiveUser(principal.id());
        return new MeResponse(user.getId(), user.getNickname(), user.getRole().name(), user.getProfileImageUrl());
    }
}
