package com.oneblog.common.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Access Token을 Authorization 헤더 대신 HttpOnly 쿠키에서 읽는다 (SEC-04).
 * 가입·로그인·refresh·로그아웃(/api/auth/**)과 정적 화면에서는 토큰을 읽지 않는다.
 * 만료된 쿠키가 남아 있어도 로그인이나 refresh가 막히지 않게 하기 위해서다.
 */
public final class CookieBearerTokenResolver {

    private CookieBearerTokenResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith("/api/") || path.startsWith("/api/auth/")) {
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (CookieNames.ACCESS_TOKEN.equals(cookie.getName())) {
                String value = cookie.getValue();
                return (value == null || value.isBlank()) ? null : value;
            }
        }
        return null;
    }
}
