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

import java.time.LocalDateTime;

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

        // 잠금 판단은 서버(JVM) 시각으로 하므로 같은 시계로 넣는다
        jdbc.update("UPDATE users SET locked_until = ? WHERE email = ?", LocalDateTime.now().minusSeconds(1),
                EMAIL);
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

    private String requestResetCode() throws Exception {
        mvc.perform(post("/api/auth/password-reset/code").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isAccepted());
        return sentResetCode();
    }

    private ResultActions reset(String code, String newPassword) throws Exception {
        return mvc.perform(post("/api/auth/password-reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"code\":\"%s\",\"newPassword\":\"%s\",\"newPasswordConfirm\":\"%s\"}"
                        .formatted(EMAIL, code, newPassword, newPassword)));
    }

    @Test
    void 재설정_번호는_30분이_지나면_쓸_수_없다() throws Exception {
        // SEC-05, 6.7 (029 US3). 만료 판단은 서버(JVM) 시각이라 같은 시계로 넣는다
        String code = requestResetCode();
        jdbc.update("UPDATE verification_codes SET expires_at = ? WHERE purpose = 'PASSWORD_RESET'",
                LocalDateTime.now().minusSeconds(1));
        reset(code, "Newpw123!").andExpect(status().isGone()).andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
        loginWith("Newpw123!").andExpect(status().isUnauthorized());
        // 발급할 때 정한 유효 시간이 30분인지
        jdbc.update("DELETE FROM verification_codes");
        requestResetCode();
        // created_at은 DB가, expires_at은 서버가 정하므로 몇 초 차이는 허용한다
        Long seconds = jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND, created_at, expires_at) FROM verification_codes "
                + "WHERE purpose = 'PASSWORD_RESET'", Long.class);
        assertThat(seconds).isBetween(30 * 60L - 5, 30 * 60L + 5);
    }

    @Test
    void 재설정_번호를_다섯_번_틀리면_맞는_번호도_쓸_수_없다() throws Exception {
        String code = requestResetCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int remaining = 4; remaining >= 1; remaining--) {
            reset(wrong, "Newpw123!").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CODE_MISMATCH"))
                    .andExpect(jsonPath("$.remainingAttempts").value(remaining));
        }
        reset(wrong, "Newpw123!").andExpect(status().isGone()).andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
        reset(code, "Newpw123!").andExpect(status().isGone()).andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
        loginWith(PASSWORD).andExpect(status().isOk());
    }

    @Test
    void 이메일_찾기_임시_토큰은_10분이_지나면_쓸_수_없다() throws Exception {
        // USR-08 ③ (029 US3)
        String found = mvc.perform(post("/api/auth/find-email").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + DEFAULT_NAME + "\",\"phone\":\"" + DEFAULT_PHONE + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(found, "$[0].token");
        Long seconds = jdbc.queryForObject(
                "SELECT TIMESTAMPDIFF(SECOND, created_at, expires_at) FROM account_lookup_tokens", Long.class);
        assertThat(seconds).isBetween(10 * 60L - 5, 10 * 60L + 5);
        jdbc.update("UPDATE account_lookup_tokens SET expires_at = ?", LocalDateTime.now().minusSeconds(1));
        mvc.perform(post("/api/auth/find-email/reset-code").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("LOOKUP_EXPIRED"));
    }

    @Test
    void 잠긴_계정도_비밀번호를_재설정하면_바로_로그인된다() throws Exception {
        // USR-06: 재설정하면 로그인 잠금도 풀린다 (029 US3)
        for (int i = 0; i < 5; i++) {
            loginWith("Wrong123!");
        }
        loginWith(PASSWORD).andExpect(status().isLocked());
        String code = requestResetCode();
        reset(code, "Newpw123!").andExpect(status().isNoContent());
        loginWith("Newpw123!").andExpect(status().isOk());
    }

    @Test
    void 재설정의_새_비밀번호도_같은_규칙을_검사한다() throws Exception {
        // SEC-02: 가입·변경·재설정 모두 같은 규칙 (029 US3)
        String code = requestResetCode();
        reset(code, "abcdefgh1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
        // 규칙 검사에서 걸린 요청은 번호를 쓰지 않는다
        reset(code, "Newpw123!").andExpect(status().isNoContent());
    }
}
