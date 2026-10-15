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
        // 블랙리스트는 이메일 또는 전화번호로 막으므로 회원마다 다른 번호를 쓴다 (029 R1)
        owner = signUpAndLogin("owner@example.com", "블로그장", "김주인", "010-1111-0001");
        member = signUpAndLogin("member@example.com", "멤버회원", "이멤버", "010-1111-0002");
        outsider = signUpAndLogin("out@example.com", "바깥사람", "박바깥", "010-1111-0003");
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
        mvc.perform(post("/api/users/멤버회원/follow").with(csrf()).cookie(outsider))
                .andExpect(status().isOk()).andExpect(jsonPath("$.following").value(true));
        // 차단 전에 팔로우가 실제로 있어야 "차단하면 끊긴다"를 확인할 수 있다 (029 US1)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follows", Integer.class)).isEqualTo(1);

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
        // 강퇴된 사람만 막힌다. 이메일·전화번호가 다른 회원은 참여할 수 있다 (029 US1)
        mvc.perform(post("/api/blogs/open-blog/join").with(csrf()).cookie(outsider))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/blogs/open-blog/membership").with(csrf()).cookie(outsider))
                .andExpect(status().isNoContent());
        // 블랙리스트에 없는 사람은 문의할 것이 없다
        mvc.perform(post("/api/blogs/open-blog/blacklist-inquiries").with(csrf()).cookie(outsider)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"풀어 주세요\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_BLACKLISTED"));
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
        // 멤버 관리 권한이 없는 멤버는 신고 목록을 볼 수 없다 (본인 신고 제외 규칙은 아래 별도 테스트, D-95)
        mvc.perform(get("/api/blogs/open-blog/reports").cookie(member))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").exists());
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

    @Test
    void 신고_대상인_부블로그장은_자기_신고를_보지도_처리하지도_못한다() throws Exception {
        // 멤버 관리 권한이 있어도 나에 대한 신고는 목록에 없고 처리할 수 없다 (D-95, 029 US1)
        addMember("open-blog", "out@example.com", "SUB_OWNER");
        jdbc.update("UPDATE blog_members SET can_manage_members = 1, sub_owner_since = NOW(6) WHERE user_id = ?",
                userId("out@example.com"));
        Long postId = writePost(outsider, "open-blog", "부블로그장 글", "본문");
        report(member, "{\"targetType\":\"POST\",\"targetId\":" + postId + ",\"reason\":\"SPAM\"}")
                .andExpect(status().isCreated());

        mvc.perform(get("/api/blogs/open-blog/reports").cookie(outsider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        String body = mvc.perform(get("/api/blogs/open-blog/reports").cookie(owner))
                .andExpect(jsonPath("$", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        Integer reportId = JsonPath.read(body, "$[0].id");
        mvc.perform(post("/api/blogs/open-blog/reports/" + reportId + "/resolve").with(csrf()).cookie(outsider)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"NO_ISSUE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CANNOT_HANDLE_OWN_REPORT"));
        assertThat(jdbc.queryForObject("SELECT status FROM reports", String.class)).isEqualTo("PENDING");
    }

    @Test
    void 부블로그장끼리와_자기_자신은_제재할_수_없고_블로그장은_부블로그장을_제재한다() throws Exception {
        // 제재 위계 (3.7, D-43, 029 US2)
        addMember("open-blog", "out@example.com", "SUB_OWNER");
        jdbc.update("UPDATE blog_members SET role = 'SUB_OWNER', can_manage_members = 1, sub_owner_since = NOW(6) "
                + "WHERE user_id IN (?, ?)", userId("member@example.com"), userId("out@example.com"));

        sanction(member, "out@example.com", "{\"type\":\"WARNING\",\"reason\":\"x\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CANNOT_SANCTION"));
        sanction(member, "member@example.com", "{\"type\":\"WARNING\",\"reason\":\"x\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CANNOT_SANCTION_SELF"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sanctions", Integer.class)).isZero();

        sanction(owner, "member@example.com", "{\"type\":\"WARNING\",\"reason\":\"부블로그장 경고\"}")
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sanctions", Integer.class)).isEqualTo(1);
    }

    @Test
    void 블로그_신고와_관리자_신고는_서로의_API로_처리할_수_없다() throws Exception {
        // 블로그장 글 신고는 관리자에게, 멤버 글 신고는 블로그장에게 간다. 서로의 처리 주소로는 처리되지 않는다 (D-96, 029 US2)
        Long ownerPost = writePost(owner, "open-blog", "블로그장 글", "본문");
        Long memberPost = writePost(member, "open-blog", "멤버 글", "본문");
        report(outsider, "{\"targetType\":\"POST\",\"targetId\":" + ownerPost + ",\"reason\":\"SPAM\"}")
                .andExpect(status().isCreated());
        report(outsider, "{\"targetType\":\"POST\",\"targetId\":" + memberPost + ",\"reason\":\"SPAM\"}")
                .andExpect(status().isCreated());
        Long adminQueue = jdbc.queryForObject("SELECT id FROM reports WHERE blog_id IS NULL", Long.class);
        Long blogQueue = jdbc.queryForObject("SELECT id FROM reports WHERE blog_id IS NOT NULL", Long.class);

        mvc.perform(post("/api/blogs/open-blog/reports/" + adminQueue + "/resolve").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"NO_ISSUE\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/reports/" + blogQueue + "/resolve").with(csrf())
                        .cookie(createAdminAndLogin("admin01"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"NO_ISSUE\",\"reason\":\"x\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BLOG_REPORT"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reports WHERE status = 'PENDING'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void 블랙리스트는_탈퇴_후_다시_가입해도_이메일이나_전화번호로_막는다() throws Exception {
        // BLG-11: 블랙리스트에 있는 이메일이나 전화번호로는 참여할 수 없다. 탈퇴 후 재가입해도 막힌다 (029 US4)
        sanction(owner, "member@example.com", "{\"type\":\"KICK\",\"reason\":\"규칙 위반\"}")
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/me/withdrawal").with(csrf()).cookie(member).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());
        // 가입 인증번호는 같은 이메일로 1분에 한 번이라 앞서 쓴 기록을 지운다
        jdbc.update("DELETE FROM verification_codes");

        Cookie sameEmail = signUpAndLogin("member@example.com", "다시온멤버", "이멤버", "010-1111-0009");
        mvc.perform(post("/api/blogs/open-blog/join").with(csrf()).cookie(sameEmail))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BLACKLISTED"));
        Cookie samePhone = signUpAndLogin("new@example.com", "새얼굴", "이멤버", "010-1111-0002");
        mvc.perform(post("/api/blogs/open-blog/join").with(csrf()).cookie(samePhone))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BLACKLISTED"));
        Cookie stranger = signUpAndLogin("other@example.com", "다른사람", "정다른", "010-1111-0010");
        mvc.perform(post("/api/blogs/open-blog/join").with(csrf()).cookie(stranger))
                .andExpect(status().isCreated());
    }

    @Test
    void 블로그장_경고는_1년_안의_것만_센다() throws Exception {
        // ADM-07, D-30: 경고가 3번 쌓이면 권한 박탈. 1년이 지난 경고는 세지 않는다 (029 US5)
        Cookie admin = createAdminAndLogin("admin01");
        Long blogId = blogId("open-blog");
        jdbc.update("UPDATE blog_members SET role = 'SUB_OWNER', sub_owner_since = NOW(6) WHERE user_id = ?",
                userId("member@example.com"));
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/admin/blogs/" + blogId + "/owner-warning").with(csrf()).cookie(admin)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"예전 경고\"}")).andExpect(status().isOk());
        }
        jdbc.update("UPDATE sanctions SET created_at = DATE_SUB(NOW(6), INTERVAL 13 MONTH) WHERE type = 'OWNER_WARNING'");

        mvc.perform(post("/api/admin/blogs/" + blogId + "/owner-warning").with(csrf()).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"새 경고\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/blogs/open-blog").cookie(owner)).andExpect(jsonPath("$.myRole").value("OWNER"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_actions WHERE action_type = 'OWNER_REVOKE'",
                Integer.class)).isZero();
    }

    @Test
    void 블랙리스트_해제_문의를_거절하면_문의한_사람에게_알린다() throws Exception {
        // BLG-12 (029 US6)
        sanction(owner, "member@example.com", "{\"type\":\"KICK\",\"reason\":\"규칙 위반\"}").andExpect(status().isNoContent());
        mvc.perform(post("/api/blogs/open-blog/blacklist-inquiries").with(csrf()).cookie(member)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"번호가 바뀌었어요\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/blogs/open-blog/blacklist-inquiries").with(csrf()).cookie(member)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"다시\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INQUIRY_PENDING"));
        Long inquiryId = jdbc.queryForObject("SELECT id FROM blacklist_inquiries", Long.class);
        mvc.perform(post("/api/blogs/open-blog/blacklist-inquiries/" + inquiryId + "/reject").with(csrf()).cookie(member))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/blogs/open-blog/blacklist-inquiries/" + inquiryId + "/reject").with(csrf()).cookie(owner))
                .andExpect(status().isNoContent());
        assertThat(notifications("member@example.com", "BLACKLIST_RESULT")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM blacklist_inquiries", String.class)).isEqualTo("REJECTED");
        mvc.perform(post("/api/blogs/open-blog/join").with(csrf()).cookie(member))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BLACKLISTED"));
    }

    @Test
    void 정지는_기간이_끝나면_풀리고_영구_정지는_풀리지_않고_다른_블로그는_쓴다() throws Exception {
        // BLG-13, 3.7 (029 US6)
        createBlog(outsider, "other-blog", "PUBLIC").andExpect(status().isCreated());
        addMember("other-blog", "member@example.com", "MEMBER");
        sanction(owner, "member@example.com", "{\"type\":\"SUSPENSION\",\"days\":3,\"reason\":\"도배\"}")
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/blogs/open-blog").cookie(member))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MEMBER_SUSPENDED"));
        // 정지돼도 다른 블로그는 그대로 쓴다
        writePost(member, "other-blog", "{\"title\":\"다른 블로그 글\",\"content\":\"본문\"}").andExpect(status().isCreated());
        // 기간이 지나면 다시 들어간다 (판단은 서버 시계라 같은 시계로 넣는다)
        jdbc.update("UPDATE blog_members SET suspended_until = ? WHERE user_id = ? AND blog_id = ?",
                java.time.LocalDateTime.now().minusSeconds(1), userId("member@example.com"), blogId("open-blog"));
        mvc.perform(get("/api/blogs/open-blog").cookie(member)).andExpect(status().isOk());

        // 기간 없이 주면 영구 정지
        sanction(owner, "member@example.com", "{\"type\":\"SUSPENSION\",\"reason\":\"반복\"}")
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT YEAR(suspended_until) FROM blog_members WHERE user_id = ? AND blog_id = ?",
                Integer.class, userId("member@example.com"), blogId("open-blog"))).isEqualTo(9999);
        mvc.perform(get("/api/blogs/open-blog").cookie(member))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MEMBER_SUSPENDED"));
    }

    @Test
    void 블로그장을_내보낼_때_부블로그장이_여럿이면_먼저_된_사람이_이어받는다() throws Exception {
        // ADM-01, ADM-07 (029 US6)
        addMember("open-blog", "out@example.com", "MEMBER");
        jdbc.update("UPDATE blog_members SET role = 'SUB_OWNER', sub_owner_since = NOW(6) - INTERVAL 1 DAY WHERE user_id = ?",
                userId("out@example.com"));
        jdbc.update("UPDATE blog_members SET role = 'SUB_OWNER', sub_owner_since = NOW(6) WHERE user_id = ?",
                userId("member@example.com"));
        Cookie admin = createAdminAndLogin("admin01");
        mvc.perform(post("/api/admin/blogs/" + blogId("open-blog") + "/owner-revoke").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"운영 방치\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/blogs/open-blog").cookie(outsider)).andExpect(jsonPath("$.myRole").value("OWNER"));
        mvc.perform(get("/api/blogs/open-blog").cookie(member)).andExpect(jsonPath("$.myRole").value("SUB_OWNER"));
        mvc.perform(get("/api/blogs/open-blog").cookie(owner)).andExpect(jsonPath("$.myRole").value("MEMBER"));
    }

    @Test
    void 신고_목록과_처리는_멤버_관리_권한이_있어야_한다() throws Exception {
        // D-71, D-138: 신고 처리는 멤버 관리 권한 (029 US6)
        jdbc.update("UPDATE blog_members SET role = 'SUB_OWNER', can_manage_members = 0, sub_owner_since = NOW(6) "
                + "WHERE user_id = ?", userId("member@example.com"));
        mvc.perform(get("/api/blogs/open-blog/reports").cookie(member))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        jdbc.update("UPDATE blog_members SET can_manage_members = 1 WHERE user_id = ?", userId("member@example.com"));
        mvc.perform(get("/api/blogs/open-blog/reports").cookie(member)).andExpect(status().isOk());
    }

    @Test
    void 신고는_2주가_지나면_다시_할_수_있다() throws Exception {
        // 6.5 신고: 같은 대상은 한 사람이 2주에 한 번 (029 US6)
        Long postId = writePost(member, "open-blog", "문제 글", "본문");
        String json = "{\"targetType\":\"POST\",\"targetId\":" + postId + ",\"reason\":\"SPAM\"}";
        report(outsider, json).andExpect(status().isCreated());
        jdbc.update("UPDATE reports SET created_at = NOW(6) - INTERVAL 13 DAY");
        report(outsider, json).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_REPORTED"));
        jdbc.update("UPDATE reports SET created_at = NOW(6) - INTERVAL 15 DAY");
        report(outsider, json).andExpect(status().isCreated());
    }

    @Test
    void 프로필_신고는_관리자에게_가고_관리자는_신고로_글을_지운다() throws Exception {
        // SOC-06, ADM-04, D-85 (029 US6)
        report(outsider, "{\"targetType\":\"PROFILE\",\"nickname\":\"멤버회원\",\"reason\":\"ABUSE\"}")
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT blog_id FROM reports WHERE target_type = 'PROFILE'", Long.class)).isNull();
        mvc.perform(get("/api/blogs/open-blog/reports").cookie(owner)).andExpect(jsonPath("$", hasSize(0)));

        // 블로그장 글을 두 명이 신고 → 관리자가 한 번에 지운다
        Long postId = writePost(owner, "open-blog", "지울 블로그장 글", "본문");
        String json = "{\"targetType\":\"POST\",\"targetId\":" + postId + ",\"reason\":\"SPAM\"}";
        report(outsider, json).andExpect(status().isCreated());
        report(member, json).andExpect(status().isCreated());
        Cookie admin = createAdminAndLogin("admin01");
        mvc.perform(get("/api/admin/reports").cookie(admin)).andExpect(jsonPath("$.items", hasSize(3)));
        Long reportId = jdbc.queryForObject("SELECT MIN(id) FROM reports WHERE target_type = 'POST'", Long.class);
        mvc.perform(post("/api/admin/reports/" + reportId + "/resolve").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"CONTENT_DELETED\",\"reason\":\"스팸\"}"))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT deleted_by FROM posts WHERE id = ?", String.class, postId)).isEqualTo("ADMIN");
        assertThat(notifications("owner@example.com", "POST_DELETED")).isEqualTo(1);
        assertThat(notifications("out@example.com", "REPORT_RESULT")).isEqualTo(1);
        assertThat(notifications("member@example.com", "REPORT_RESULT")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reports WHERE target_type = 'POST' AND status = 'RESOLVED'",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_actions WHERE action_type = 'REPORT_RESOLVE'",
                Integer.class)).isEqualTo(1);
        // 글이 지워져도 신고 당시 내용이 남는다
        mvc.perform(get("/api/admin/reports").param("status", "RESOLVED").cookie(admin))
                .andExpect(jsonPath("$.items[0].snapshot").value(containsString("지울 블로그장 글")));
    }

    @Test
    void 관리자의_블로그장_경고와_강제_폐쇄는_기록이_남는다() throws Exception {
        // SEC-09, ADM-06 (029 US6)
        Cookie admin = createAdminAndLogin("admin01");
        mvc.perform(post("/api/admin/blogs/" + blogId("open-blog") + "/owner-warning").with(csrf()).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"경고\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/admin/blogs/" + blogId("open-blog") + "/close").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"불법 정보\"}"))
                .andExpect(status().isNoContent());
        for (String type : new String[] {"OWNER_WARNING", "BLOG_FORCE_CLOSE"}) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_actions WHERE action_type = ?", Integer.class, type))
                    .as(type).isEqualTo(1);
        }
    }
}
