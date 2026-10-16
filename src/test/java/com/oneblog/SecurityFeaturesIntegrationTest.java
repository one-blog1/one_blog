package com.oneblog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.oneblog.common.security.CookieNames;
import com.oneblog.common.security.TurnstileVerifier;

import jakarta.servlet.http.Cookie;

/**
 * 테스트 설정에서 꺼 둔 보안 기능을 켜고 확인한다 (029 US3, research R2).
 * - 요청 제한 (SEC-03, D-80, USR-08): app.rate-limit.enabled=true
 * - Secure 쿠키 (SEC-04): app.cookie.secure=true (운영 값)
 * - 3번 실패 뒤 사람 확인 (SEC-13, 6.7): Turnstile은 목. 토큰 "ok"만 통과
 * 요청 제한 기록은 앱 메모리에 남아 이 클래스의 테스트끼리 섞이므로, 테스트마다 다른 IP를 쓴다.
 * 가입·기본 로그인 도우미는 127.0.0.1을 쓰므로 이 클래스에서는 가입을 적게 한다(가입 인증번호는 같은 IP 10분에 10번).
 */
@TestPropertySource(properties = {"app.rate-limit.enabled=true", "app.cookie.secure=true"})
class SecurityFeaturesIntegrationTest extends IntegrationTestSupport {

    @MockitoBean
    private TurnstileVerifier turnstile;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void captchaOn() {
        when(turnstile.enabled()).thenReturn(true);
        when(turnstile.siteKey()).thenReturn("test-site-key");
        when(turnstile.verify(any(), any())).thenReturn(false);
        when(turnstile.verify(eq("ok"), anyString())).thenReturn(true);
    }

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private ResultActions login(String ip, String email, String password, String captchaToken, boolean rememberMe)
            throws Exception {
        String token = captchaToken == null ? "null" : "\"" + captchaToken + "\"";
        return mvc.perform(post("/api/auth/login").with(csrf()).with(fromIp(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"rememberMe\":" + rememberMe
                        + ",\"captchaToken\":" + token + "}"));
    }

    private ResultActions adminLogin(String ip, String loginId, String password) throws Exception {
        return mvc.perform(post("/api/auth/admin/login").with(csrf()).with(fromIp(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}"));
    }

    private static String setCookie(MvcResult result, String name) {
        List<String> headers = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        return headers.stream().filter(h -> h.startsWith(name + "=")).findFirst()
                .orElseThrow(() -> new AssertionError(name + " 쿠키가 없습니다: " + headers));
    }

    @Test
    void 세_번_틀리면_사람_확인을_해야_로그인된다() throws Exception {
        String ip = "10.29.1.1";
        signUp("captcha@example.com", "확인회원", "김확인", "010-4444-0001");
        login(ip, "captcha@example.com", "Wrong123!", null, false)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        login(ip, "captcha@example.com", "Wrong123!", null, false)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        // 3번째 실패부터 화면에 사람 확인을 띄우라고 알린다
        login(ip, "captcha@example.com", "Wrong123!", null, false)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("LOGIN_FAILED_CAPTCHA"));

        // 그 뒤에는 비밀번호가 맞아도 사람 확인 없이(또는 틀린 토큰으로)는 안 된다
        login(ip, "captcha@example.com", PASSWORD, null, false)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAPTCHA_REQUIRED"));
        login(ip, "captcha@example.com", PASSWORD, "forged", false)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAPTCHA_REQUIRED"));
        login(ip, "captcha@example.com", PASSWORD, "ok", false).andExpect(status().isOk());

        // 관리자는 여러 번 틀려도 사람 확인을 하지 않는다 (D-105)
        jdbc.update("INSERT INTO users (login_id, password_hash, role, status) VALUES ('admin29', ?, 'ADMIN', 'ACTIVE')",
                passwordEncoder.encode(PASSWORD));
        for (int i = 0; i < 4; i++) {
            adminLogin(ip, "admin29", "Wrong123!").andExpect(status().isUnauthorized());
        }
        adminLogin(ip, "admin29", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void 같은_IP의_로그인은_10분에_30번까지다() throws Exception {
        String ip = "10.29.2.1";
        // 계정 잠금·사람 확인에 걸리지 않도록 없는 이메일로 센다
        for (int i = 0; i < 30; i++) {
            login(ip, "nobody" + i + "@example.com", "Wrong123!", null, false)
                    .andExpect(status().isUnauthorized());
        }
        login(ip, "nobody@example.com", "Wrong123!", null, false)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.retryAfterSeconds").exists());
        // 관리자 로그인도 같은 제한에 들어간다 (D-80)
        adminLogin(ip, "admin", "Wrong123!")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        // 다른 IP는 영향이 없다
        login("10.29.2.2", "nobody@example.com", "Wrong123!", null, false).andExpect(status().isUnauthorized());
    }

    @Test
    void 이메일_찾기는_같은_IP에서_10분에_5번까지다() throws Exception {
        String ip = "10.29.3.1";
        String body = "{\"name\":\"없는사람\",\"phone\":\"010-9999-0000\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/find-email").with(csrf()).with(fromIp(ip))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/auth/find-email").with(csrf()).with(fromIp(ip))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void 운영_설정에서_로그인_쿠키는_Secure_HttpOnly다() throws Exception {
        String ip = "10.29.4.1";
        signUp("cookie@example.com", "쿠키회원", "박쿠키", "010-4444-0002");

        // 로그인 유지를 끄면 두 쿠키 모두 브라우저를 닫으면 사라진다 (Max-Age 없음)
        MvcResult session = login(ip, "cookie@example.com", PASSWORD, null, false)
                .andExpect(status().isOk()).andReturn();
        for (String name : new String[] {CookieNames.ACCESS_TOKEN, CookieNames.REFRESH_TOKEN}) {
            String header = setCookie(session, name);
            assertThat(header).contains("Secure").contains("HttpOnly").doesNotContain("Max-Age");
        }

        // 로그인 유지를 켜면 두 쿠키 모두 14일
        MvcResult remembered = login(ip, "cookie@example.com", PASSWORD, null, true)
                .andExpect(status().isOk()).andReturn();
        for (String name : new String[] {CookieNames.ACCESS_TOKEN, CookieNames.REFRESH_TOKEN}) {
            assertThat(setCookie(remembered, name)).contains("Secure").contains("HttpOnly")
                    .contains("Max-Age=1209600");
        }

        // 비밀번호 재확인 쿠키는 10분 (D-102)
        Cookie access = remembered.getResponse().getCookie(CookieNames.ACCESS_TOKEN);
        MvcResult reauth = mvc.perform(post("/api/me/reauth").with(csrf()).cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(setCookie(reauth, CookieNames.REAUTH_TICKET)).contains("Secure").contains("HttpOnly")
                .contains("Max-Age=600");

        // 가입 티켓 쿠키도 Secure
        jdbc.update("DELETE FROM verification_codes");
        String code = requestSignupCode("ticket@example.com");
        MvcResult verified = mvc.perform(post("/api/auth/signup/email-code/verify").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ticket@example.com\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(setCookie(verified, CookieNames.SIGNUP_TICKET)).contains("Secure").contains("HttpOnly");
    }
}
