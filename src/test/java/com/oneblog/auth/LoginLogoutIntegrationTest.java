package com.oneblog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MvcResult;

import com.oneblog.IntegrationTestSupport;

import jakarta.servlet.http.Cookie;

/** 로그인·로그아웃과 권한 거부 (USR-03, USR-04, SEC-04, SEC-10, quickstart 7~9, 12, 14). */
class LoginLogoutIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "login@example.com";

    @Autowired
    private JwtEncoder jwtEncoder;

    @BeforeEach
    void createMember() throws Exception {
        signUp(EMAIL, "로그인회원");
    }

    @Test
    void 이메일_대소문자가_달라도_로그인되고_HttpOnly_쿠키를_준다() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Login@Example.COM\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("로그인회원"))
                .andReturn();
        Cookie access = result.getResponse().getCookie("ACCESS_TOKEN");
        Cookie refresh = result.getResponse().getCookie("REFRESH_TOKEN");
        assertThat(access).isNotNull();
        assertThat(refresh).isNotNull();
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(refresh.isHttpOnly()).isTrue();

        mvc.perform(get("/api/me").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("로그인회원"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void 비밀번호가_틀리거나_없는_이메일이면_같은_문구() throws Exception {
        String wrongPassword = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"Wrong123!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(unknownEmail).isEqualTo(wrongPassword);
    }

    @Test
    void 로그인하지_않으면_내_정보를_볼_수_없다() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 로그아웃하면_이전_토큰으로_아무것도_할_수_없다() throws Exception {
        MvcResult login = login(EMAIL, false);
        Cookie access = login.getResponse().getCookie("ACCESS_TOKEN");
        Cookie refresh = login.getResponse().getCookie("REFRESH_TOKEN");

        mvc.perform(post("/api/auth/logout").with(csrf()).cookie(access, refresh))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE revoked_at IS NOT NULL",
                Integer.class)).isEqualTo(1);
        mvc.perform(get("/api/me").cookie(access))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/auth/refresh").with(csrf()).cookie(refresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    void CSRF_토큰_없는_로그인은_거부한다() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 만료된_Access_Token은_TOKEN_EXPIRED이고_refresh로_다시_받는다() throws Exception {
        MvcResult login = login(EMAIL, false);
        Cookie refresh = login.getResponse().getCookie("REFRESH_TOKEN");
        Long userId = jdbc.queryForObject("SELECT id FROM users", Long.class);
        Long sessionId = jdbc.queryForObject("SELECT id FROM refresh_tokens", Long.class);

        Instant past = Instant.now().minusSeconds(3600);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(past.minusSeconds(600))
                .expiresAt(past)
                .claim("sid", String.valueOf(sessionId))
                .claim("typ", "ACCESS")
                .build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mvc.perform(get("/api/me").cookie(new Cookie("ACCESS_TOKEN", expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));

        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").with(csrf()).cookie(refresh))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie newAccess = refreshed.getResponse().getCookie("ACCESS_TOKEN");
        assertThat(newAccess).isNotNull();
        mvc.perform(get("/api/me").cookie(newAccess)).andExpect(status().isOk());
    }

    @Test
    void 가입_티켓을_Access_Token처럼_쓸_수_없다() throws Exception {
        jdbc.update("UPDATE verification_codes SET created_at = DATE_SUB(NOW(6), INTERVAL 2 MINUTE)");
        Cookie ticket = verifyCode("ticket@example.com", requestSignupCode("ticket@example.com"));
        mvc.perform(get("/api/me").cookie(new Cookie("ACCESS_TOKEN", ticket.getValue())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
