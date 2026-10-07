package com.oneblog.common.security;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.oneblog.common.config.AppProperties;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 로그인·가입 쿠키를 만들고 지운다. 모두 HttpOnly라 화면 스크립트가 읽을 수 없다 (SEC-04).
 * 경로·속성은 contracts/auth-api.md "쿠키" 표를 따른다.
 */
@Component
public class AuthCookies {

    public static final String ACCESS_PATH = "/";
    public static final String REFRESH_PATH = "/api/auth";
    public static final String SIGNUP_PATH = "/api/auth/signup";
    public static final Duration SIGNUP_TICKET_TTL = Duration.ofMinutes(30);

    private final boolean secure;

    public AuthCookies(AppProperties properties) {
        this.secure = properties.cookie().secure();
    }

    /** 로그인 유지 체크 시 14일, 아니면 브라우저를 닫으면 사라지는 세션 쿠키. */
    public void setLoginCookies(HttpServletResponse response, String accessToken, String refreshToken,
            boolean rememberMe) {
        add(response, base(CookieNames.ACCESS_TOKEN, accessToken, ACCESS_PATH, "Lax", rememberMe));
        add(response, base(CookieNames.REFRESH_TOKEN, refreshToken, REFRESH_PATH, "Lax", rememberMe));
    }

    public void setAccessCookie(HttpServletResponse response, String accessToken, boolean rememberMe) {
        add(response, base(CookieNames.ACCESS_TOKEN, accessToken, ACCESS_PATH, "Lax", rememberMe));
    }

    public void clearLoginCookies(HttpServletResponse response) {
        add(response, expired(CookieNames.ACCESS_TOKEN, ACCESS_PATH, "Lax"));
        add(response, expired(CookieNames.REFRESH_TOKEN, REFRESH_PATH, "Lax"));
    }

    public void setSignupTicket(HttpServletResponse response, String ticket) {
        add(response, ResponseCookie.from(CookieNames.SIGNUP_TICKET, ticket)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(SIGNUP_PATH)
                .maxAge(SIGNUP_TICKET_TTL)
                .build());
    }

    public void clearSignupTicket(HttpServletResponse response) {
        add(response, expired(CookieNames.SIGNUP_TICKET, SIGNUP_PATH, "Strict"));
    }

    private ResponseCookie base(String name, String value, String path, String sameSite, boolean rememberMe) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path);
        if (rememberMe) {
            builder.maxAge(com.oneblog.auth.RefreshToken.REMEMBER_ME_TTL);
        }
        return builder.build();
    }

    private ResponseCookie expired(String name, String path, String sameSite) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(0)
                .build();
    }

    private void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
