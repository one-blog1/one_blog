package com.oneblog.auth.social;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.oneblog.common.config.AppProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 소셜 로그인 쿠키 (D-126). 모두 HttpOnly.
 * - SOCIAL_PENDING: 가입 대기 정보. 가입·연결 API(/api/auth)에만 보낸다
 * - SOCIAL_LINK: 내 정보에서 연결하기를 눌렀다는 표시. 사이트에서 돌아오는 주소(/login/oauth2)에만 보낸다
 * 사이트에서 돌아오는 이동은 다른 사이트에서 시작되므로 SameSite=Lax로 둔다 (Strict면 실리지 않는다).
 */
@Component
public class SocialCookies {

    public static final String PENDING = "SOCIAL_PENDING";
    public static final String PENDING_PATH = "/api/auth";
    public static final String LINK = "SOCIAL_LINK";
    public static final String LINK_PATH = SocialAuthorizationRequestCookies.PATH;

    private final boolean secure;

    public SocialCookies(AppProperties properties) {
        this.secure = properties.cookie().secure();
    }

    public void setPending(HttpServletResponse response, String value) {
        add(response, PENDING, value, PENDING_PATH, SocialTickets.PENDING_TTL);
    }

    public void clearPending(HttpServletResponse response) {
        add(response, PENDING, "", PENDING_PATH, Duration.ZERO);
    }

    public void setLink(HttpServletResponse response, String value) {
        add(response, LINK, value, LINK_PATH, SocialTickets.LINK_TTL);
    }

    public void clearLink(HttpServletResponse response) {
        add(response, LINK, "", LINK_PATH, Duration.ZERO);
    }

    public static String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void add(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge)
                .build()
                .toString());
    }
}
