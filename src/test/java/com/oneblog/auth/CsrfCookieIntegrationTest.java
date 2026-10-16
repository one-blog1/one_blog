package com.oneblog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import com.oneblog.IntegrationTestSupport;

import jakarta.servlet.http.Cookie;

/**
 * CSRF를 화면과 같은 경로로 확인한다: 응답 쿠키 XSRF-TOKEN → 요청 헤더 X-XSRF-TOKEN (SEC-10, 029 US3, research R4).
 * 테스트 도구의 csrf()는 한 번 쓰면 같은 Spring 컨텍스트의 CSRF 저장소를 세션용으로 바꿔 버린다(028에서 발견).
 * 그래서 이 클래스는 따로 컨텍스트를 띄우고(test.context 속성), 회원도 가입 API 대신 DB에 바로 넣어 csrf()를 한 번도 쓰지 않는다.
 */
@TestPropertySource(properties = "test.context=real-csrf")
class CsrfCookieIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void CSRF는_쿠키와_같은_값을_헤더로_보내야_통과한다() throws Exception {
        jdbc.update("""
                INSERT INTO users (email, password_hash, name, nickname, phone, role, status)
                VALUES ('csrf@example.com', ?, '홍길동', '씨에스알에프', '01012345678', 'USER', 'ACTIVE')""",
                passwordEncoder.encode(PASSWORD));
        MvcResult page = mvc.perform(get("/login.html")).andExpect(status().isOk()).andReturn();
        Cookie xsrf = page.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).as("첫 화면 응답에 XSRF-TOKEN 쿠키가 있다").isNotNull();
        assertThat(xsrf.isHttpOnly()).as("화면 스크립트가 읽어야 하므로 HttpOnly가 아니다").isFalse();
        String body = "{\"email\":\"csrf@example.com\",\"password\":\"" + PASSWORD + "\"}";

        mvc.perform(post("/api/auth/login").cookie(xsrf).header("X-XSRF-TOKEN", "wrong-" + xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").cookie(xsrf)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }
}
