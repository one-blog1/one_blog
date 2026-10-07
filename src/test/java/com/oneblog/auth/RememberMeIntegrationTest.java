package com.oneblog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.oneblog.IntegrationTestSupport;

import jakarta.servlet.http.Cookie;

/** "로그인 유지"와 30분 무활동 로그아웃 (SEC-04, D-61, D-62, quickstart 10, 11). */
class RememberMeIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "remember@example.com";

    @BeforeEach
    void createMember() throws Exception {
        signUp(EMAIL, "유지회원");
    }

    private LocalDateTime expiresAt() {
        return jdbc.queryForObject("SELECT expires_at FROM refresh_tokens", LocalDateTime.class);
    }

    @Test
    void 체크하지_않으면_세션_쿠키이고_체크하면_14일() throws Exception {
        MvcResult session = login(EMAIL, false);
        assertThat(session.getResponse().getCookie("REFRESH_TOKEN").getMaxAge()).isEqualTo(-1);

        jdbc.update("DELETE FROM refresh_tokens");
        MvcResult remembered = login(EMAIL, true);
        assertThat(remembered.getResponse().getCookie("REFRESH_TOKEN").getMaxAge())
                .isEqualTo((int) Duration.ofDays(14).toSeconds());
        assertThat(expiresAt()).isAfter(LocalDateTime.now().plusDays(13));
    }

    @Test
    void 삼십분_무활동이_지나면_로그아웃된다() throws Exception {
        MvcResult login = login(EMAIL, false);
        Cookie access = login.getResponse().getCookie("ACCESS_TOKEN");
        Cookie refresh = login.getResponse().getCookie("REFRESH_TOKEN");

        jdbc.update("UPDATE refresh_tokens SET expires_at = DATE_SUB(NOW(6), INTERVAL 1 SECOND)");

        mvc.perform(get("/api/me").cookie(access).header("X-User-Activity", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/auth/refresh").with(csrf()).cookie(refresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    void 직접_한_행동만_삼십분을_다시_시작한다() throws Exception {
        MvcResult login = login(EMAIL, false);
        Cookie access = login.getResponse().getCookie("ACCESS_TOKEN");

        // 마지막 활동을 10분 전으로 돌린다 (만료까지 20분 남음)
        jdbc.update("UPDATE refresh_tokens SET last_activity_at = DATE_SUB(NOW(6), INTERVAL 10 MINUTE), "
                + "expires_at = DATE_ADD(NOW(6), INTERVAL 20 MINUTE)");
        LocalDateTime before = expiresAt();

        // 자동 요청(헤더 없음)은 연장하지 않는다
        mvc.perform(get("/api/me").cookie(access)).andExpect(status().isOk());
        assertThat(expiresAt()).isEqualTo(before);

        // 직접 한 행동은 지금부터 30분으로 연장한다
        mvc.perform(get("/api/me").cookie(access).header("X-User-Activity", "1")).andExpect(status().isOk());
        assertThat(expiresAt()).isAfter(LocalDateTime.now().plusMinutes(29));
    }

    @Test
    void 로그인_유지는_활동이_있어도_만료_시각이_바뀌지_않는다() throws Exception {
        MvcResult login = login(EMAIL, true);
        Cookie access = login.getResponse().getCookie("ACCESS_TOKEN");
        jdbc.update("UPDATE refresh_tokens SET last_activity_at = DATE_SUB(NOW(6), INTERVAL 10 MINUTE)");
        LocalDateTime before = expiresAt();

        mvc.perform(get("/api/me").cookie(access).header("X-User-Activity", "1")).andExpect(status().isOk());
        assertThat(expiresAt()).isEqualTo(before);
    }
}
