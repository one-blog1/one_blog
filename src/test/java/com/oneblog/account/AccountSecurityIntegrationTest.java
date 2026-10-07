package com.oneblog.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.oneblog.IntegrationTestSupport;

import jakarta.servlet.http.Cookie;

/** 로그인 잠금·비밀번호 찾기·이메일 찾기·보안 헤더 (SEC-03, SEC-05, SEC-12, SEC-13, USR-06, USR-08 / specs/015-account-security). */
class AccountSecurityIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "alice@example.com";

    @BeforeEach
    void setUp() throws Exception {
        signUp(EMAIL, "앨리스");
    }

    private ResultActions loginWith(String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}"));
    }

    private String sentResetCode() {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailService, atLeastOnce()).sendPasswordResetCode(eq(EMAIL), code.capture());
        return code.getValue();
    }

    @Test
    void 다섯_번_틀리면_5분_잠기고_풀리면_다시_로그인한다() throws Exception {
        for (int i = 0; i < 4; i++) {
            loginWith("Wrong123!").andExpect(status().isUnauthorized());
        }
        loginWith("Wrong123!").andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(300));
        // 잠긴 동안에는 맞는 비밀번호도 안 된다
        loginWith(PASSWORD).andExpect(status().isLocked());

        jdbc.update("UPDATE users SET locked_until = DATE_SUB(NOW(6), INTERVAL 1 SECOND) WHERE email = ?", EMAIL);
        loginWith(PASSWORD).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT failed_login_count FROM users WHERE email = ?", Integer.class, EMAIL))
                .isZero();
        // 사람 확인 키가 없으면 꺼져 있다 (개발·테스트)
        mvc.perform(get("/api/auth/captcha-config")).andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void 비밀번호를_재설정하면_모든_로그인이_끝나고_번호는_한_번만_쓴다() throws Exception {
        Cookie session = loginCookie(EMAIL);
        // 가입하지 않은 이메일도 같은 응답 (메일은 보내지 않음)
        mvc.perform(post("/api/auth/password-reset/code").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("입력한 이메일로 안내를 보냈습니다."));
        verify(mailService, never()).sendPasswordResetCode(eq("nobody@example.com"), anyString());
        mvc.perform(post("/api/auth/password-reset/code").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isAccepted());
        String code = sentResetCode();
        String wrong = code.equals("000000") ? "111111" : "000000";

        String body = "{\"email\":\"%s\",\"code\":\"%s\",\"newPassword\":\"Newpw123!\",\"newPasswordConfirm\":\"Newpw123!\"}";
        mvc.perform(post("/api/auth/password-reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(EMAIL, wrong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.remainingAttempts").value(4));
        mvc.perform(post("/api/auth/password-reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(EMAIL, code)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
        loginWith("Newpw123!").andExpect(status().isOk());
        // 같은 번호로 다시는 못 바꾼다
        mvc.perform(post("/api/auth/password-reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(EMAIL, code)))
                .andExpect(status().isGone());
    }

    @Test
    void 이메일_찾기는_가린_이메일과_가입일을_주고_임시_토큰으로_재설정한다() throws Exception {
        String found = mvc.perform(post("/api/auth/find-email").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" 홍길동 \",\"phone\":\"010-1234-5678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value(containsString("*")))
                .andExpect(jsonPath("$[0].joinedAt").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(found).doesNotContain(EMAIL);
        String token = JsonPath.read(found, "$[0].token");

        mvc.perform(post("/api/auth/find-email").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"없는사람\",\"phone\":\"01099998888\"}"))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(post("/api/auth/find-email/reset-code").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"wrong-token\"}"))
                .andExpect(status().isGone());
        mvc.perform(post("/api/auth/find-email/reset-code").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isAccepted());
        String code = sentResetCode();
        mvc.perform(post("/api/auth/password-reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lookupToken\":\"" + token + "\",\"code\":\"" + code
                                + "\",\"newPassword\":\"Newpw123!\",\"newPasswordConfirm\":\"Newpw123!\"}"))
                .andExpect(status().isNoContent());
        loginWith("Newpw123!").andExpect(status().isOk());
        // 쓴 토큰은 다시 못 쓴다
        mvc.perform(post("/api/auth/find-email/reset-code").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isGone());
    }

    @Test
    void 화면에_보안_헤더가_붙는다() throws Exception {
        mvc.perform(get("/login.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("X-Frame-Options"));
    }
}
