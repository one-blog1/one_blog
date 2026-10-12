package com.oneblog.auth.social;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.stereotype.Component;

import com.oneblog.auth.AccessTokenService;
import com.oneblog.common.config.AppProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 카카오·네이버·구글로 보낼 때의 요청(state, PKCE 값 등)을 세션 대신 서명한 쿠키에 둔다 (D-126, SCL-01).
 * 돌아오는 주소(/login/oauth2/code/*)에만 보내고, 다른 사이트에서 돌아오는 이동에도 실리도록 SameSite=Lax로 둔다.
 * 쿠키를 고치면 서명이 맞지 않아 버려지고, 돌아왔을 때 state가 쿠키 값과 같아야 로그인이 이어진다(CSRF 방지).
 */
@Component
public class SocialAuthorizationRequestCookies implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String COOKIE = "OAUTH2_REQUEST";
    public static final String PATH = "/login/oauth2";
    static final Duration TTL = Duration.ofMinutes(10);
    private static final String TYPE = "OAUTH2_REQUEST";

    private final SocialTickets tickets;
    private final boolean secure;

    public SocialAuthorizationRequestCookies(SocialTickets tickets, AppProperties properties) {
        this.tickets = tickets;
        this.secure = properties.cookie().secure();
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return read(request).orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request,
            HttpServletResponse response) {
        if (authorizationRequest == null) {
            clear(response);
            return;
        }
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(now.plus(TTL))
                .claim(AccessTokenService.CLAIM_TYPE, TYPE)
                .claim("au", authorizationRequest.getAuthorizationUri())
                .claim("cid", authorizationRequest.getClientId())
                .claim("ru", String.valueOf(authorizationRequest.getRedirectUri()))
                .claim("sc", List.copyOf(authorizationRequest.getScopes()))
                .claim("st", String.valueOf(authorizationRequest.getState()))
                .claim("ap", strings(authorizationRequest.getAdditionalParameters()))
                .claim("at", strings(authorizationRequest.getAttributes()))
                .claim("aru", authorizationRequest.getAuthorizationRequestUri())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(tickets.encode(claims), TTL).toString());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
            HttpServletResponse response) {
        Optional<OAuth2AuthorizationRequest> loaded = read(request);
        clear(response);
        return loaded.orElse(null);
    }

    private Optional<OAuth2AuthorizationRequest> read(HttpServletRequest request) {
        return tickets.decode(cookieValue(request), TYPE).map(SocialAuthorizationRequestCookies::toRequest);
    }

    @SuppressWarnings("unchecked")
    private static OAuth2AuthorizationRequest toRequest(Jwt jwt) {
        Map<String, Object> additional = new LinkedHashMap<>((Map<String, Object>) jwt.getClaims().get("ap"));
        Map<String, Object> attributes = new LinkedHashMap<>((Map<String, Object>) jwt.getClaims().get("at"));
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(jwt.getClaimAsString("au"))
                .clientId(jwt.getClaimAsString("cid"))
                .redirectUri(jwt.getClaimAsString("ru"))
                .scopes(new HashSet<>(jwt.getClaimAsStringList("sc")))
                .state(jwt.getClaimAsString("st"))
                .additionalParameters(additional)
                .attributes(attributes)
                .authorizationRequestUri(jwt.getClaimAsString("aru"))
                .build();
    }

    /** 값은 모두 글자(state, code_challenge, registration_id 등)라 글자로 저장한다. */
    private static Map<String, String> strings(Map<String, Object> values) {
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (value != null) {
                result.put(key, String.valueOf(value));
            }
        });
        return result;
    }

    private static String cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
