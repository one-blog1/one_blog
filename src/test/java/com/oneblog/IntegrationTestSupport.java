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
import org.springframework.beans.factory.annotation.Value;
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
 * 통합 테스트 공통: 테스트 DB(기본 one_blog_test)를 쓰고, 테스트마다 회원·인증 테이블을 비운다 (research R8).
 * 개발 DB를 실수로 비우지 않도록, 접속한 DB 이름이 DB_NAME과 같으면 테스트를 멈춘다.
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

    @Value("${DB_NAME:one_blog}")
    private String devDatabaseName;

    @BeforeEach
    void cleanTables() {
        String current = jdbc.queryForObject("SELECT DATABASE()", String.class);
        if (current == null || current.equalsIgnoreCase(devDatabaseName)) {
            throw new IllegalStateException("테스트가 개발 DB(" + current + ")에 연결됐습니다. .env의 TEST_DB_NAME을 다른 DB로 지정하세요.");
        }
        // 기능이 늘 때마다 테이블을 적지 않도록, flyway 기록을 뺀 모든 테이블을 한 연결에서 비운다.
        // 테이블 이름은 information_schema에서 읽은 값이라 사용자 입력이 섞이지 않는다.
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                java.util.List<String> tables = new java.util.ArrayList<>();
                try (var rs = statement.executeQuery("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
                          AND table_name <> 'flyway_schema_history'""")) {
                    while (rs.next()) {
                        tables.add(rs.getString(1));
                    }
                }
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                try {
                    for (String table : tables) {
                        statement.executeUpdate("DELETE FROM `" + table + "`");
                    }
                } finally {
                    statement.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
            }
            return null;
        });
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

    /** 로그인하고 ACCESS_TOKEN 쿠키를 돌려준다. 로그인이 필요한 요청에 .cookie(...)로 붙인다. */
    protected Cookie loginCookie(String email) throws Exception {
        return login(email, false).getResponse().getCookie(CookieNames.ACCESS_TOKEN);
    }

    /** 가입하고 로그인해 ACCESS_TOKEN 쿠키를 돌려준다. */
    protected Cookie signUpAndLogin(String email, String nickname) throws Exception {
        signUp(email, nickname);
        return loginCookie(email);
    }

    /** 회원을 관리자로 바꾼다 (관리자 로그인 화면은 007). 인증 필터가 매 요청 DB에서 권한을 읽으므로 바로 반영된다. */
    protected void makeAdmin(String email, String loginId) {
        jdbc.update("UPDATE users SET role = 'ADMIN', login_id = ? WHERE email = ?", loginId, email.toLowerCase());
    }
}
