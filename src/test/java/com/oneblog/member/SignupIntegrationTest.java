package com.oneblog.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.oneblog.IntegrationTestSupport;

import jakarta.servlet.http.Cookie;

/** 가입 완료 규칙 (USR-01, SEC-01, SEC-02, 6.5, quickstart 1, 5, 6). */
class SignupIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "gildong@example.com";

    private Cookie verifiedTicket() throws Exception {
        return verifyCode(EMAIL, requestSignupCode(EMAIL));
    }

    @Test
    void 세_단계를_마치면_가입되고_비밀번호는_bcrypt로_저장된다() throws Exception {
        signUp(EMAIL, "길동이");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT email, nickname, phone, password_hash, role, terms_agreed_at, privacy_agreed_at FROM users");
        assertThat(row.get("email")).isEqualTo(EMAIL);
        assertThat(row.get("nickname")).isEqualTo("길동이");
        assertThat(row.get("phone")).isEqualTo("01012345678");
        assertThat((String) row.get("password_hash")).startsWith("$2").doesNotContain(PASSWORD);
        assertThat(row.get("role")).isEqualTo("USER");
        assertThat(row.get("terms_agreed_at")).isNotNull();
        assertThat(row.get("privacy_agreed_at")).isNotNull();
    }

    @Test
    void 인증_없이_가입을_요청하면_거부한다() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(signupBody("길동이")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGNUP_TICKET_INVALID"));
    }

    @Test
    void 위조한_가입_티켓은_거부한다() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf())
                        .cookie(new Cookie("SIGNUP_TICKET", "forged.token.value"))
                        .contentType(MediaType.APPLICATION_JSON).content(signupBody("길동이")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGNUP_TICKET_INVALID"));
    }

    @Test
    void 같은_가입_티켓은_두번_쓸_수_없다() throws Exception {
        Cookie ticket = verifiedTicket();
        mvc.perform(post("/api/auth/signup").with(csrf()).cookie(ticket)
                        .contentType(MediaType.APPLICATION_JSON).content(signupBody("길동이")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/signup").with(csrf()).cookie(ticket)
                        .contentType(MediaType.APPLICATION_JSON).content(signupBody("다른닉")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGNUP_TICKET_INVALID"));
    }

    @Test
    void 이미_쓰이는_닉네임이나_예약어는_쓸_수_없다() throws Exception {
        signUp("first@example.com", "길동이");

        mvc.perform(post("/api/auth/signup").with(csrf()).cookie(verifiedTicket())
                        .contentType(MediaType.APPLICATION_JSON).content(signupBody("길동이")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_UNAVAILABLE"));

        jdbc.update("UPDATE verification_codes SET created_at = DATE_SUB(NOW(6), INTERVAL 2 MINUTE)");
        mvc.perform(post("/api/auth/signup").with(csrf()).cookie(verifiedTicket())
                        .contentType(MediaType.APPLICATION_JSON).content(signupBody("Admin")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_UNAVAILABLE"));
    }

    @Test
    void 규칙에_맞지_않는_입력은_400() throws Exception {
        Cookie ticket = verifiedTicket();
        String body = """
                {"password":"short","passwordConfirm":"short","name":"홍길동","nickname":"<script>",
                 "phone":"12345","agreeTerms":true,"agreePrivacy":true}
                """;
        mvc.perform(post("/api/auth/signup").with(csrf()).cookie(ticket)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
    }

    @Test
    void 필수_동의가_없으면_400() throws Exception {
        Cookie ticket = verifiedTicket();
        String body = signupBody("길동이").replace("\"agreePrivacy\":true", "\"agreePrivacy\":false");
        mvc.perform(post("/api/auth/signup").with(csrf()).cookie(ticket)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 닉네임_중복_확인() throws Exception {
        signUp(EMAIL, "길동이");
        mvc.perform(get("/api/auth/signup/nickname-availability").param("nickname", "길동이"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("TAKEN"));
        mvc.perform(get("/api/auth/signup/nickname-availability").param("nickname", "운영자"))
                .andExpect(jsonPath("$.reason").value("RESERVED"));
        mvc.perform(get("/api/auth/signup/nickname-availability").param("nickname", "새닉네임"))
                .andExpect(jsonPath("$.available").value(true));
    }
}
