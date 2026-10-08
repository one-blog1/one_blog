package com.oneblog.sanction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.oneblog.post.PostTestSupport;

import jakarta.servlet.http.Cookie;

/** 차단·제재·블랙리스트·신고·관리자 처리 (BLG-11~13, SOC-05, SOC-06, ADM-02, ADM-04, ADM-07 / specs/013-moderation). */
class ModerationIntegrationTest extends PostTestSupport {

    private Cookie owner;
    private Cookie member;
    private Cookie outsider;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUpAndLogin("owner@example.com", "블로그장");
        member = signUpAndLogin("member@example.com", "멤버회원");
        outsider = signUpAndLogin("out@example.com", "바깥사람");
        createBlog(owner, "open-blog", "PUBLIC").andExpect(status().isCreated());
        addMember("open-blog", "member@example.com", "MEMBER");
    }

    private ResultActions sanction(Cookie by, String email, String json) throws Exception {
        return mvc.perform(post("/api/blogs/open-blog/members/" + userId(email) + "/sanctions").with(csrf()).cookie(by)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions report(Cookie by, String json) throws Exception {
        return mvc.perform(post("/api/reports").with(csrf()).cookie(by).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private int notifications(String email, String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications n JOIN users u ON u.id = n.user_id "
                + "WHERE u.email = ? AND n.type = ?", Integer.class, email, type);
    }

    @Test
    void 차단하면_글과_댓글이_가려지고_팔로우가_끊긴다() throws Exception {
        Long postId = writePost(member, "open-blog", "멤버의 글", "본문");
        mvc.perform(post("/api/posts/" + postId + "/comments").with(csrf()).cookie(member)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"멤버 댓글\"}"));
        mvc.perform(post("/api/users/멤버회원/follow").with(csrf()).cookie(outsider));

        mvc.perform(post("/api/users/멤버회원/block").with(csrf()).cookie(outsider))
                .andExpect(status().isOk()).andExpect(jsonPath("$.blocked").value(true));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follows", Integer.class)).isZero();
        mvc.perform(post("/api/users/멤버회원/follow").with(csrf()).cookie(outsider)).andExpect(status().isForbidden());

        mvc.perform(get("/api/feed").cookie(outsider)).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/blogs/open-blog/posts").cookie(outsider)).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(outsider)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/me/blocks").cookie(outsider)).andExpect(jsonPath("$[0].nickname").value("멤버회원"));

        // 같은 블로그의 멤버끼리는 차단해도 그 블로그 안의 글이 보인다 (D-36)
        addMember("open-blog", "out@example.com", "MEMBER");
        mvc.perform(get("/api/blogs/open-blog/posts").cookie(outsider)).andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(get("/api/feed").cookie(outsider)).andExpect(jsonPath("$.items", hasSize(1)));

        // 다시 누르면 해제
        mvc.perform(post("/api/users/멤버회원/block").with(csrf()).cookie(outsider))
                .andExpect(jsonPath("$.blocked").value(false));
    }

    @Test
    void 블로그장이_차단한_회원의_참여_신청은_자동_거절된다() throws Exception {
        mvc.perform(post("/api/users/바깥사람/block").with(csrf()).cookie(owner));
        mvc.perform(post("/api/blogs/open-blog/join").with(csrf()).cookie(outsider))
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(get("/api/blogs/open-blog").cookie(outsider)).andExpect(jsonPath("$.myRole").doesNotExist());
    }

    @Test
    void 정지된_멤버는_그_블로그에_들어갈_수_없고_해제하면_돌아온다() throws Exception {
        sanction(owner, "member@example.com", "{\"type\":\"WARNING\",\"reason\":\"도배\"}").andExpect(status().isNoContent());
        sanction(owner, "member@example.com", "{\"type\":\"SUSPENSION\",\"days\":7,\"reason\":\"도배\"}")
                .andExpect(status().isBadRequest());
        sanction(owner, "member@example.com", "{\"type\":\"SUSPENSION\",\"days\":3,\"reason\":\"도배\"}")
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/blogs/open-blog").cookie(member))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_SUSPENDED"))
                .andExpect(jsonPath("$.message").value(containsString("도배")));
        writePost(member, "open-blog", "{\"title\":\"정지 중\",\"content\":\"본문\"}").andExpect(status().isForbidden());
        mvc.perform(get("/api/blogs/open-blog/members").cookie(owner))
                .andExpect(jsonPath("$[1].suspensionCount").value(1))
                .andExpect(jsonPath("$[1].suspendedUntil").exists());
        assertThat(notifications("member@example.com", "SANCTION")).isEqualTo(2);

        sanction(owner, "member@example.com", "{\"type\":\"RELEASE\"}").andExpect(status().isNoContent());
        mvc.perform(get("/api/blogs/open-blog").cookie(member)).andExpect(status().isOk());
    }

    @Test
    void 제재_권한과_대상을_검사한다() throws Exception {
        // 일반 멤버는 제재할 수 없고, 블로그장은 제재 대상이 아니다
        sanction(member, "owner@example.com", "{\"type\":\"WARNING\",\"reason\":\"x\"}").andExpect(status().isForbidden());
        Cookie sub = signUpAndLogin("sub@example.com", "부블로그장");
        addMember("open-blog", "sub@example.com", "SUB_OWNER");
        // 멤버 관리 권한이 없는 부블로그장
        sanction(sub, "member@example.com", "{\"type\":\"WARNING\",\"reason\":\"x\"}").andExpect(status().isForbidden());
        jdbc.update("UPDATE blog_members SET can_manage_members = 1 WHERE user_id = ?", userId("sub@example.com"));
        sanction(sub, "member@example.com", "{\"type\":\"WARNING\",\"reason\":\"x\"}").andExpect(status().isNoContent());
        sanction(sub, "owner@example.com", "{\"type\":\"WARNING\",\"reason\":\"x\"}").andExpect(status().isForbidden());
        sanction(owner, "member@example.com", "{\"type\":\"WARNING\",\"reason\":\"  \"}").andExpect(status().isBadRequest());
    }

    @Test
    void 강제_퇴장하면_블랙리스트에_올라_다시_참여할_수_없고_문의로_해제한다() throws Exception {
        Long postId = writePost(member, "open-blog", "멤버 글", "본문");
        sanction(owner, "member@example.com", "{\"type\":\"KICK\",\"reason\":\"규칙 위반\"}").andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.authorName").value("탈퇴한 계정"));
        mvc.perform(get("/api/blogs/open-blog")).andExpect(jsonPath("$.memberCount").value(1));
        mvc.perform(get("/api/blogs/open-blog/blacklist").cookie(owner)).andExpect(jsonPath("$", hasSize(1)));
        assertThat(jdbc.queryForObject("SELECT email_hash FROM blog_blacklists", String.class)).hasSize(64)
                .doesNotContain("member");

        mvc.perform(post("/api/blogs/open-blog/join").with(csrf()).cookie(member))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BLACKLISTED"));
        // 블랙리스트에 없는 사람은 문의할 것이 없다 (테스트 회원은 전화번호가 모두 같아 바깥사람의 번호를 바꾼다)
        jdbc.update("UPDATE users SET phone = '01099990000' WHERE email = 'out@example.com'");
        mvc.perform(post("/api/blogs/open-blog/blacklist-inquiries").with(csrf()).cookie(outsider)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"풀어 주세요\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/blogs/open-blog/blacklist-inquiries").with(csrf()).cookie(member)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"번호가 바뀌었어요\"}"))
                .andExpect(status().isCreated());
        String body = mvc.perform(get("/api/blogs/open-blog/blacklist-inquiries").cookie(owner))
                .andExpect(jsonPath("$[0].nameMatched").value(true))
                .andExpect(jsonPath("$[0].phoneMatched").value(true))
                .andReturn().getResponse().getContentAsString();
        Integer inquiryId = JsonPath.read(body, "$[0].id");
        mvc.perform(post("/api/blogs/open-blog/blacklist-inquiries/" + inquiryId + "/release").with(csrf()).cookie(member))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/blogs/open-blog/blacklist-inquiries/" + inquiryId + "/release").with(csrf()).cookie(owner))
                .andExpect(status().isNoContent());
        assertThat(notifications("member@example.com", "BLACKLIST_RESULT")).isEqualTo(1);
        mvc.perform(post("/api/blogs/open-blog/join").with(csrf()).cookie(member)).andExpect(status().isCreated());
    }

    @Test
    void 블로그_안_신고는_블로그장이_처리하고_신고자에게_알린다() throws Exception {
        Long postId = writePost(member, "open-blog", "문제 글", "본문");
        addMember("open-blog", "out@example.com", "MEMBER");
        String reportJson = "{\"targetType\":\"POST\",\"targetId\":" + postId + ",\"reason\":\"SPAM\"}";
        report(member, reportJson).andExpect(status().isBadRequest());
        report(outsider, reportJson).andExpect(status().isCreated());
        report(outsider, reportJson).andExpect(status().isConflict());
        report(outsider, "{\"targetType\":\"POST\",\"targetId\":" + postId + ",\"reason\":\"NONE\"}")
                .andExpect(status().isBadRequest());

        String body = mvc.perform(get("/api/blogs/open-blog/reports").cookie(owner))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].targetNickname").value("멤버회원"))
                .andReturn().getResponse().getContentAsString();
        Integer reportId = JsonPath.read(body, "$[0].id");
        // 신고 대상 본인(멤버)은 신고를 볼 수 없다
        mvc.perform(get("/api/blogs/open-blog/reports").cookie(member)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reports").cookie(createAdminAndLogin("admin01")))
                .andExpect(jsonPath("$.items", hasSize(0)));

        mvc.perform(post("/api/blogs/open-blog/reports/" + reportId + "/resolve").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"SUSPENSION\",\"days\":14,\"reason\":\"스팸\"}"))
                .andExpect(status().isNoContent());
        assertThat(notifications("out@example.com", "REPORT_RESULT")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT report_id FROM sanctions", Long.class)).isEqualTo(reportId.longValue());
        mvc.perform(get("/api/blogs/open-blog").cookie(member)).andExpect(status().isForbidden());
    }

    @Test
    void 블로그장_글_신고는_관리자가_처리하고_경고_3번이면_권한을_박탈한다() throws Exception {
        Long postId = writePost(owner, "open-blog", "블로그장 글", "본문");
        report(member, "{\"targetType\":\"POST\",\"targetId\":" + postId + ",\"reason\":\"ABUSE\",\"detail\":\"욕설\"}")
                .andExpect(status().isCreated());
        mvc.perform(get("/api/blogs/open-blog/reports").cookie(owner)).andExpect(jsonPath("$", hasSize(0)));
        Cookie admin = createAdminAndLogin("admin01");
        String body = mvc.perform(get("/api/admin/reports").cookie(admin))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        Integer reportId = JsonPath.read(body, "$.items[0].id");
        mvc.perform(post("/api/admin/reports/" + reportId + "/resolve").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"OWNER_WARNING\",\"reason\":\"욕설\"}"))
                .andExpect(status().isNoContent());
        assertThat(notifications("member@example.com", "REPORT_RESULT")).isEqualTo(1);
        assertThat(notifications("owner@example.com", "OWNER_SANCTION")).isEqualTo(1);

        // 부블로그장을 두고 경고 2번 더 → 권한 박탈, 부블로그장이 블로그장이 된다
        jdbc.update("UPDATE blog_members SET role = 'SUB_OWNER', sub_owner_since = NOW(6) WHERE user_id = ?",
                userId("member@example.com"));
        Long blogId = blogId("open-blog");
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/admin/blogs/" + blogId + "/owner-warning").with(csrf()).cookie(admin)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"반복\"}")).andExpect(status().isOk());
        }
        mvc.perform(get("/api/blogs/open-blog").cookie(member)).andExpect(jsonPath("$.myRole").value("OWNER"));
        mvc.perform(get("/api/blogs/open-blog").cookie(owner)).andExpect(jsonPath("$.myRole").value("MEMBER"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_actions WHERE action_type = 'OWNER_REVOKE'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void 부블로그장이_없으면_권한_박탈은_폐쇄_예약이고_강제_폐쇄는_철회할_수_없다() throws Exception {
        Cookie admin = createAdminAndLogin("admin01");
        Long blogId = blogId("open-blog");
        mvc.perform(post("/api/admin/blogs/" + blogId + "/owner-revoke").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"운영 방치\"}"))
                .andExpect(jsonPath("$.outcome").value("CLOSING"));
        mvc.perform(get("/api/blogs/open-blog")).andExpect(jsonPath("$.status").value("CLOSING"));
        assertThat(notifications("member@example.com", "BLOG_CLOSING")).isEqualTo(1);

        createBlog(owner, "second-blog", "PUBLIC").andExpect(status().isCreated());
        mvc.perform(post("/api/admin/blogs/" + blogId("second-blog") + "/close").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"불법 정보\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/blogs/second-blog/close").with(csrf()).cookie(owner))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/blogs/" + blogId + "/close").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
    }
}
