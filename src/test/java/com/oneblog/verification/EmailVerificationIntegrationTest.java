package com.oneblog.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.oneblog.IntegrationTestSupport;

/** 이메일 인증번호 규칙 (USR-02, D-28, quickstart 2~4). */
class EmailVerificationIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "new-user@example.com";

    private String verifyBody(String code) {
        return "{\"email\":\"" + EMAIL + "\",\"code\":\"" + code + "\"}";
    }

    private String wrongCode(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    @Test
    void 인증번호를_보내면_202와_안내문구를_준다() throws Exception {
        mvc.perform(post("/api/auth/signup/email-code").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("인증번호를 보냈습니다."))
                .andExpect(jsonPath("$.resendAvailableInSeconds").value(60));
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM verification_codes", Integer.class);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void 인증번호는_원문으로_저장하지_않는다() throws Exception {
        String code = requestSignupCode(EMAIL);
        String hash = jdbc.queryForObject("SELECT code_hash FROM verification_codes", String.class);
        assertThat(hash).isNotEqualTo(code).hasSize(64);
    }

    @Test
    void 일분_안에_다시_보내면_429() throws Exception {
        requestSignupCode(EMAIL);
        mvc.perform(post("/api/auth/signup/email-code").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RESEND_TOO_SOON"))
                .andExpect(jsonPath("$.retryAfterSeconds").exists());
    }

    @Test
    void 틀린_번호는_남은_횟수를_주고_다섯번째에_무효() throws Exception {
        String code = requestSignupCode(EMAIL);
        for (int remaining = 4; remaining >= 1; remaining--) {
            mvc.perform(post("/api/auth/signup/email-code/verify").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(verifyBody(wrongCode(code))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CODE_MISMATCH"))
                    .andExpect(jsonPath("$.remainingAttempts").value(remaining));
        }
        mvc.perform(post("/api/auth/signup/email-code/verify").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(verifyBody(wrongCode(code))))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
        // 맞는 번호를 넣어도 이미 무효
        mvc.perform(post("/api/auth/signup/email-code/verify").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(verifyBody(code)))
                .andExpect(status().isGone());
    }

    @Test
    void 십분이_지난_번호는_만료() throws Exception {
        String code = requestSignupCode(EMAIL);
        jdbc.update("UPDATE verification_codes SET expires_at = DATE_SUB(NOW(6), INTERVAL 1 SECOND)");
        mvc.perform(post("/api/auth/signup/email-code/verify").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(verifyBody(code)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
    }

    @Test
    void 맞는_번호면_가입_티켓_쿠키를_준다() throws Exception {
        String code = requestSignupCode(EMAIL);
        MvcResult result = mvc.perform(post("/api/auth/signup/email-code/verify").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(verifyBody(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andReturn();
        assertThat(result.getResponse().getCookie("SIGNUP_TICKET")).isNotNull();
        assertThat(result.getResponse().getCookie("SIGNUP_TICKET").isHttpOnly()).isTrue();
    }

    @Test
    void 가입된_이메일도_같은_응답을_주고_안내메일만_보낸다() throws Exception {
        signUp("member@example.com", "가입회원");
        jdbc.update("UPDATE verification_codes SET created_at = DATE_SUB(NOW(6), INTERVAL 2 MINUTE)");

        String newEmailBody = mvc.perform(post("/api/auth/signup/email-code").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"other@example.com\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String registeredBody = mvc.perform(post("/api/auth/signup/email-code").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"MEMBER@example.com\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(registeredBody).isEqualTo(newEmailBody);
        verify(mailService).sendAlreadyRegistered("member@example.com");
    }

    @Test
    void CSRF_토큰_없는_요청은_거부한다() throws Exception {
        mvc.perform(post("/api/auth/signup/email-code")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isForbidden());
        verify(mailService, never()).sendSignupCode(anyString(), anyString());
    }
}
