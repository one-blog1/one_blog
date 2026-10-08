package com.oneblog.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.oneblog.IntegrationTestSupport;

import jakarta.servlet.http.Cookie;

/** 회원정보 수정 (USR-07, SEC-02, SEC-04, D-102 / specs/009-social-profile, specs/016-home-account-ui). */
class AccountIntegrationTest extends IntegrationTestSupport {

    private Cookie alice;
    private Cookie reauth;

    @BeforeEach
    void setUp() throws Exception {
        alice = signUpAndLogin("alice@example.com", "앨리스");
        signUp("bob@example.com", "밥아저씨");
        reauth = reauth(alice, PASSWORD);
    }

    /** 비밀번호 재확인 후 받은 REAUTH_TICKET 쿠키. */
    private Cookie reauth(Cookie login, String password) throws Exception {
        Cookie ticket = mvc.perform(post("/api/me/reauth").with(csrf()).cookie(login)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andReturn().getResponse().getCookie("REAUTH_TICKET");
        assertThat(ticket).isNotNull();
        assertThat(ticket.isHttpOnly()).isTrue();
        assertThat(ticket.getPath()).isEqualTo("/api/me");
        return ticket;
    }

    @Test
    void 비밀번호를_다시_확인해야_프로필과_비밀번호를_바꿀_수_있다() throws Exception {
        String profile = "{\"nickname\":\"새앨리스\",\"phone\":\"01012345678\"}";
        mvc.perform(put("/api/me/profile").with(csrf()).cookie(alice).contentType(MediaType.APPLICATION_JSON)
                        .content(profile))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REAUTH_REQUIRED"));
        mvc.perform(put("/api/me/password").with(csrf()).cookie(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"Newpw123!\",\"newPasswordConfirm\":\"Newpw123!\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REAUTH_REQUIRED"));

        // 틀린 비밀번호로는 티켓을 주지 않는다
        mvc.perform(post("/api/me/reauth").with(csrf()).cookie(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Wrong123!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));

        mvc.perform(get("/api/me/reauth").cookie(alice)).andExpect(jsonPath("$.verified").value(false));
        mvc.perform(get("/api/me/reauth").cookie(alice, reauth)).andExpect(jsonPath("$.verified").value(true));

        // 다른 기기(다른 로그인)에서는 이 티켓을 쓸 수 없다
        Cookie otherDevice = loginCookie("alice@example.com");
        mvc.perform(put("/api/me/profile").with(csrf()).cookie(otherDevice, reauth)
                        .contentType(MediaType.APPLICATION_JSON).content(profile))
                .andExpect(status().isForbidden());
        // 다른 회원도 마찬가지
        Cookie bob = loginCookie("bob@example.com");
        mvc.perform(put("/api/me/profile").with(csrf()).cookie(bob, reauth)
                        .contentType(MediaType.APPLICATION_JSON).content(profile))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/me/profile").with(csrf()).cookie(alice, reauth).contentType(MediaType.APPLICATION_JSON)
                        .content(profile))
                .andExpect(status().isOk());
    }

    @Test
    void 상단_메뉴용_내_정보에_프로필_사진_주소가_있다() throws Exception {
        jdbc.update("UPDATE users SET profile_image_url = '/files/abc.png' WHERE email = 'alice@example.com'");
        mvc.perform(get("/api/me").cookie(alice))
                .andExpect(jsonPath("$.nickname").value("앨리스"))
                .andExpect(jsonPath("$.profileImageUrl").value("/files/abc.png"));
    }

    @Test
    void 내_정보를_보고_닉네임_전화번호_소개를_바꾼다() throws Exception {
        // 비밀번호를 다시 확인하기 전에는 가려서 보인다 (D-110)
        mvc.perform(get("/api/me/account").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("al***@example.com"))
                .andExpect(jsonPath("$.name").value("홍*동"))
                .andExpect(jsonPath("$.phone").value("010-****-5678"))
                .andExpect(jsonPath("$.masked").value(true));
        // 재확인 뒤라도 내 정보 화면(full 없이)은 가린다
        mvc.perform(get("/api/me/account").cookie(alice, reauth)).andExpect(jsonPath("$.masked").value(true));
        mvc.perform(get("/api/me/account").param("full", "true").cookie(alice)).andExpect(jsonPath("$.masked").value(true));
        mvc.perform(get("/api/me/account").param("full", "true").cookie(alice, reauth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.phone").value("010-1234-5678"))
                .andExpect(jsonPath("$.masked").value(false));

        mvc.perform(put("/api/me/profile").with(csrf()).cookie(alice, reauth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"새앨리스\",\"phone\":\"010-9999-8888\",\"bio\":\"  안녕하세요  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새앨리스"))
                .andExpect(jsonPath("$.phone").value("010-9999-8888"))
                .andExpect(jsonPath("$.bio").value("안녕하세요"));
        mvc.perform(get("/api/users/새앨리스")).andExpect(jsonPath("$.bio").value("안녕하세요"));
    }

    @Test
    void 남이_쓰는_닉네임과_틀린_형식은_거부한다() throws Exception {
        mvc.perform(put("/api/me/profile").with(csrf()).cookie(alice, reauth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"밥아저씨\",\"phone\":\"01012345678\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_UNAVAILABLE"));
        mvc.perform(put("/api/me/profile").with(csrf()).cookie(alice, reauth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"a\",\"phone\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'nickname')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'phone')]").exists());
        // 지금 닉네임 그대로 저장하는 것은 된다
        mvc.perform(put("/api/me/profile").with(csrf()).cookie(alice, reauth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"앨리스\",\"phone\":\"01012345678\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 남의_사진_파일은_프로필로_쓸_수_없다() throws Exception {
        Long bobId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'bob@example.com'", Long.class);
        jdbc.update("""
                INSERT INTO files (user_id, purpose, stored_name, original_name, content_type, size_bytes)
                VALUES (?, 'PROFILE', '0123456789abcdef0123456789abcdef.png', 'a.png', 'image/png', 10)
                """, bobId);
        Long fileId = jdbc.queryForObject("SELECT id FROM files", Long.class);
        mvc.perform(put("/api/me/profile").with(csrf()).cookie(alice, reauth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"앨리스\",\"phone\":\"01012345678\",\"profileFileId\":" + fileId + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROFILE_FILE"));
    }

    @Test
    void 비밀번호를_바꾸면_다른_기기는_로그아웃된다() throws Exception {
        Cookie otherDevice = loginCookie("alice@example.com");

        mvc.perform(put("/api/me/password").with(csrf()).cookie(alice, reauth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"short\",\"newPasswordConfirm\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));

        mvc.perform(put("/api/me/password").with(csrf()).cookie(alice, reauth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"Newpw123!\",\"newPasswordConfirm\":\"Newpw123!\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/me").cookie(alice)).andExpect(status().isOk());
        mvc.perform(get("/api/me").cookie(otherDevice)).andExpect(status().isUnauthorized());
        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE revoked_at IS NULL", Integer.class);
        assertThat(active).isEqualTo(1);
    }

    @Test
    void 관리자와_비회원은_쓸_수_없다() throws Exception {
        mvc.perform(get("/api/me/account")).andExpect(status().isUnauthorized());
        Cookie admin = createAdminAndLogin("admin01");
        mvc.perform(get("/api/me/account").cookie(admin)).andExpect(status().isForbidden());
    }
}
