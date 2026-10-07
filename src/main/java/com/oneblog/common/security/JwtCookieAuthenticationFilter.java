package com.oneblog.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import com.oneblog.auth.AccessTokenService;
import com.oneblog.auth.SessionService;
import com.oneblog.member.User;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ACCESS_TOKEN 쿠키로 로그인 사용자를 확인한다 (contracts/auth-api.md "인증 필터 동작").
 * 1. JWT 서명·만료 확인. 만료면 401 TOKEN_EXPIRED (화면이 refresh 후 다시 시도).
 * 2. 토큰 내용만 믿지 않고 로그인 행(sid)과 회원 상태를 DB에서 확인 (constitution III).
 *    폐기·만료·비활성이면 쿠키를 지우고 401 UNAUTHENTICATED.
 * 3. X-User-Activity: 1 이면 30분 무활동 시계를 다시 시작 (D-62).
 *
 * Spring의 Resource Server 설정은 Bearer 토큰 요청을 CSRF 검사에서 빼는데, 이 프로젝트는 토큰을 쿠키로 보내므로
 * CSRF 검사가 필요하다 (SEC-10). 그래서 직접 필터를 둔다. 빈으로 등록하지 않고 SecurityConfig에서 만든다.
 */
public class JwtCookieAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ACTIVITY_HEADER = "X-User-Activity";

    private final JwtDecoder jwtDecoder;
    private final SessionService sessionService;
    private final AuthCookies authCookies;

    public JwtCookieAuthenticationFilter(JwtDecoder jwtDecoder, SessionService sessionService,
            AuthCookies authCookies) {
        this.jwtDecoder = jwtDecoder;
        this.sessionService = sessionService;
        this.authCookies = authCookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = CookieBearerTokenResolver.resolve(request);
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException e) {
            if (isExpired(e)) {
                writeUnauthorized(response, "TOKEN_EXPIRED", "로그인 시간이 지났습니다.");
            } else {
                authCookies.clearLoginCookies(response);
                writeUnauthorized(response, "UNAUTHENTICATED", "로그인이 필요합니다.");
            }
            return;
        }

        Optional<User> user = validate(jwt, request);
        if (user.isEmpty()) {
            authCookies.clearLoginCookies(response);
            writeUnauthorized(response, "UNAUTHENTICATED", "로그인이 필요합니다.");
            return;
        }

        Long sessionId = Long.valueOf(jwt.getClaimAsString(AccessTokenService.CLAIM_SESSION_ID));
        AuthenticatedUser principal = new AuthenticatedUser(user.get().getId(), sessionId, user.get().getRole());
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Optional<User> validate(Jwt jwt, HttpServletRequest request) {
        if (!AccessTokenService.TYPE_ACCESS.equals(jwt.getClaimAsString(AccessTokenService.CLAIM_TYPE))) {
            return Optional.empty();
        }
        Long userId = parseLong(jwt.getSubject());
        Long sessionId = parseLong(jwt.getClaimAsString(AccessTokenService.CLAIM_SESSION_ID));
        if (userId == null || sessionId == null) {
            return Optional.empty();
        }
        boolean userActivity = "1".equals(request.getHeader(USER_ACTIVITY_HEADER));
        return sessionService.validate(userId, sessionId, userActivity);
    }

    private static boolean isExpired(JwtException e) {
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("expired")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void writeUnauthorized(HttpServletResponse response, String code, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }

    private static Long parseLong(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
