package com.oneblog.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.oneblog.IntegrationTestSupport;

import jakarta.servlet.http.Cookie;

/** 회원정보 수정 (USR-07, SEC-02, SEC-04 / specs/009-social-profile). */
class AccountIntegrationTest extends IntegrationTestSupport {

    private Cookie alice;

    @BeforeEach
    void setUp() throws Exception {
        alice = signUpAndLogin("alice@example.com", "앨리스");
        signUp("bob@example.com", "밥아저씨");
    }

    @Test
    void 내_정보를_보고_닉네임_전화번호_소개를_바꾼다() throws Exception {
        mvc.perform(get("/api/me/account").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.phone").value("010-1234-5678"));

        mvc.perform(put("/api/me/profile").with(csrf()).cookie(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"새앨리스\",\"phone\":\"010-9999-8888\",\"bio\":\"  안녕하세요  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새앨리스"))
                .andExpect(jsonPath("$.phone").value("010-9999-8888"))
                .andExpect(jsonPath("$.bio").value("안녕하세요"));
        mvc.perform(get("/api/users/새앨리스")).andExpect(jsonPath("$.bio").value("안녕하세요"));
    }

    @Test
    void 남이_쓰는_닉네임과_틀린_형식은_거부한다() throws Exception {
        mvc.perform(put("/api/me/profile").with(csrf()).cookie(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"밥아저씨\",\"phone\":\"01012345678\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_UNAVAILABLE"));
        mvc.perform(put("/api/me/profile").with(csrf()).cookie(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"a\",\"phone\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'nickname')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'phone')]").exists());
        // 지금 닉네임 그대로 저장하는 것은 된다
        mvc.perform(put("/api/me/profile").with(csrf()).cookie(alice).contentType(MediaType.APPLICATION_JSON)
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
        mvc.perform(put("/api/me/profile").with(csrf()).cookie(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"앨리스\",\"phone\":\"01012345678\",\"profileFileId\":" + fileId + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROFILE_FILE"));
    }

    @Test
    void 비밀번호를_바꾸면_다른_기기는_로그아웃된다() throws Exception {
        Cookie otherDevice = loginCookie("alice@example.com");

        mvc.perform(put("/api/me/password").with(csrf()).cookie(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Wrong123!\",\"newPassword\":\"Newpw123!\",\"newPasswordConfirm\":\"Newpw123!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"));
        mvc.perform(put("/api/me/password").with(csrf()).cookie(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"short\",\"newPasswordConfirm\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));

        mvc.perform(put("/api/me/password").with(csrf()).cookie(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"Newpw123!\",\"newPasswordConfirm\":\"Newpw123!\"}"))
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
