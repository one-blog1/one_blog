package com.oneblog;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.oneblog.common.security.CookieNames;
import com.oneblog.mail.MailService;

import jakarta.servlet.http.Cookie;

/**
 * 통합 테스트 공통: 로컬 MySQL의 one_blog_test DB를 쓰고, 테스트마다 회원·인증 테이블을 비운다 (research R8).
 * 메일은 가짜(MailService 목)로 바꿔 보낸 인증번호를 읽는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    protected static final String PASSWORD = "Abcd123!";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @MockitoBean
    protected MailService mailService;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM verification_codes");
        jdbc.update("DELETE FROM users");
    }

    /** 인증번호를 요청하고, 가짜 메일로 보낸 번호를 돌려준다. */
    protected String requestSignupCode(String email) throws Exception {
        mvc.perform(post("/api/auth/signup/email-code").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailService, atLeastOnce()).sendSignupCode(eq(email.toLowerCase()), code.capture());
        return code.getValue();
    }

    /** 인증번호를 확인하고 가입 티켓 쿠키를 돌려준다. */
    protected Cookie verifyCode(String email, String code) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/signup/email-code/verify").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie(CookieNames.SIGNUP_TICKET);
    }

    protected String signupBody(String nickname) {
        return """
                {"password":"%s","passwordConfirm":"%s","name":"홍길동","nickname":"%s",
                 "phone":"010-1234-5678","agreeTerms":true,"agreePrivacy":true}
                """.formatted(PASSWORD, PASSWORD, nickname);
    }

    /** 가입 세 단계를 끝까지 진행한다. */
    protected void signUp(String email, String nickname) throws Exception {
        String code = requestSignupCode(email);
        Cookie ticket = verifyCode(email, code);
        mvc.perform(post("/api/auth/signup").with(csrf()).cookie(ticket)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody(nickname)))
                .andExpect(status().isCreated());
    }

    /** 로그인하고 응답(쿠키 포함)을 돌려준다. */
    protected MvcResult login(String email, boolean rememberMe) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                                + "\",\"rememberMe\":" + rememberMe + "}"))
                .andExpect(status().isOk())
                .andReturn();
    }
}
