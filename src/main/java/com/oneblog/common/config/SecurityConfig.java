package com.oneblog.common.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import com.oneblog.auth.SessionService;
import com.oneblog.common.security.AuthCookies;
import com.oneblog.common.security.JwtCookieAuthenticationFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 인증·인가·CSRF 설정 (SEC-04, SEC-10, SEC-11, research R3·R4).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
            SessionService sessionService, AuthCookies authCookies) throws Exception {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // XSRF-TOKEN 쿠키(JS가 읽음) + X-XSRF-TOKEN 헤더 (SEC-10)
                .csrf(csrf -> csrf.spa())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/signup.html", "/login.html",
                                "/blog-new.html", "/my-blogs.html", "/blog.html", "/post.html", "/post-edit.html",
                                "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers("/error").permitAll()
                        // 블로그 첫 화면과 업로드 이미지, 블로그 목록·첫 화면 정보는 비회원도 본다.
                        // 볼 수 있는지는 BlogAccessService가 판단한다 (research R7, R10)
                        .requestMatchers(HttpMethod.GET, "/blog/*", "/blog/*/posts/*", "/blog/*/write",
                                "/blog/*/posts/*/edit", "/files/*", "/api/blogs", "/api/blogs/*",
                                "/api/blogs/*/posts", "/api/posts/*")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/signup/nickname-availability").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint()))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                // 같은 위치에 넣은 필터는 넣은 순서대로 동작한다: CSRF 쿠키 → 로그인 확인
                .addFilterAfter(new JwtCookieAuthenticationFilter(jwtDecoder, sessionService, authCookies),
                        CsrfFilter.class);
        return http.build();
    }

    /** 로그인하지 않은 요청에 401 JSON으로 답한다. */
    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, exception) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"message\":\"로그인이 필요합니다.\"}");
        };
    }

    /** 모든 응답에 XSRF-TOKEN 쿠키가 실리도록 CSRF 토큰을 미리 꺼낸다 (첫 화면 로드에서 쿠키를 받기 위해). */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
