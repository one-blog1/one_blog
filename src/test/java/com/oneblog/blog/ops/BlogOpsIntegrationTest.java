package com.oneblog.blog.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.oneblog.batch.DailyBatch;
import com.oneblog.post.PostTestSupport;

import jakarta.servlet.http.Cookie;

/** 블로그 운영: 정보 수정·부블로그장·탈퇴·위임·폐쇄·04:00 배치 (BLG-01, BLG-07~09, D-71 / specs/012-blog-ops). */
class BlogOpsIntegrationTest extends PostTestSupport {

    @Autowired
    private DailyBatch dailyBatch;

    private Cookie owner;
    private Cookie member;
    private Cookie subscriber;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUpAndLogin("owner@example.com", "블로그장");
        member = signUpAndLogin("member@example.com", "멤버회원");
        subscriber = signUpAndLogin("sub@example.com", "구독자");
        createBlog(owner, "open-blog", "PUBLIC").andExpect(status().isCreated());
        addMember("open-blog", "member@example.com", "MEMBER");
        mvc.perform(post("/api/blogs/open-blog/subscription").with(csrf()).cookie(subscriber));
    }

    private Long memberId() {
        return userId("member@example.com");
    }

    private int notifications(String email, String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications n JOIN users u ON u.id = n.user_id "
                + "WHERE u.email = ? AND n.type = ?", Integer.class, email, type);
    }

    @Test
    void 정보는_정보_수정_권한이_있어야_고친다() throws Exception {
        String body = "{\"name\":\"새 이름\",\"description\":\"새 소개\",\"tags\":[\"새태그\"]}";
        mvc.perform(put("/api/blogs/open-blog/info").with(csrf()).cookie(member)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/blogs/open-blog/members/" + memberId() + "/sub-owner").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subOwner\":true,\"canEditInfo\":true,\"canManageMembers\":false,\"canManagePosts\":false}"))
                .andExpect(status().isNoContent());
        mvc.perform(put("/api/blogs/open-blog/info").with(csrf()).cookie(member)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        mvc.perform(get("/api/blogs/open-blog"))
                .andExpect(jsonPath("$.name").value("새 이름"))
                .andExpect(jsonPath("$.tags[0]").value("새태그"));
        // 부블로그장도 공개 범위는 못 바꾼다 (블로그장만)
        mvc.perform(put("/api/blogs/open-blog/settings").with(csrf()).cookie(member)
                .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isForbidden());
        // 멤버 정보는 멤버 관리 권한이 있어야 본다
        mvc.perform(get("/api/blogs/open-blog/members").cookie(member)).andExpect(status().isForbidden());
        mvc.perform(get("/api/blogs/open-blog/members").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[1].role").value("SUB_OWNER"))
                .andExpect(jsonPath("$[1].email").value(containsString("*")));
    }

    @Test
    void 비공개로_바꾸면_멤버가_아닌_구독자에게_알린다() throws Exception {
        mvc.perform(put("/api/blogs/open-blog/settings").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PRIVATE\",\"joinPolicy\":\"APPROVAL\"}"))
                .andExpect(status().isNoContent());
        assertThat(notifications("sub@example.com", "BLOG_PRIVATE")).isEqualTo(1);
        assertThat(notifications("member@example.com", "BLOG_PRIVATE")).isZero();
        mvc.perform(get("/api/blogs/open-blog").cookie(subscriber)).andExpect(status().isForbidden());

        // 공개 블로그 3개를 가진 상태에서 비공개를 공개로 바꾸면 개수 제한에 걸린다 (BLG-10)
        createBlog(owner, "p2", "PUBLIC").andExpect(status().isCreated());
        createBlog(owner, "p3", "PUBLIC").andExpect(status().isCreated());
        createBlog(owner, "p4", "UNLISTED").andExpect(status().isCreated());
        mvc.perform(put("/api/blogs/open-blog/settings").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BLOG_LIMIT_EXCEEDED"));
    }

    @Test
    void 블로그를_나가면_글은_탈퇴한_계정이_되고_블로그장은_못_나간다() throws Exception {
        Long postId = writePost(member, "open-blog", "멤버 글", "본문");
        mvc.perform(delete("/api/blogs/open-blog/membership").with(csrf()).cookie(owner))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/blogs/open-blog/membership").with(csrf()).cookie(member))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.authorName").value("탈퇴한 계정"));
        mvc.perform(get("/api/blogs/open-blog")).andExpect(jsonPath("$.memberCount").value(1));
    }

    @Test
    void 위임을_수락하면_블로그장이_바뀐다() throws Exception {
        mvc.perform(post("/api/blogs/open-blog/transfer").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"toUserId\":" + userId("sub@example.com") + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/blogs/open-blog/transfer").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"toUserId\":" + memberId() + "}"))
                .andExpect(status().isOk());
        assertThat(notifications("member@example.com", "TRANSFER_REQUEST")).isEqualTo(1);
        String body = mvc.perform(get("/api/me/transfer-requests").cookie(member))
                .andExpect(jsonPath("$[0].blogSlug").value("open-blog"))
                .andReturn().getResponse().getContentAsString();
        Integer id = com.jayway.jsonpath.JsonPath.read(body, "$[0].id");

        // 다른 사람은 수락할 수 없다
        mvc.perform(post("/api/transfer-requests/" + id + "/accept").with(csrf()).cookie(subscriber))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/transfer-requests/" + id + "/accept").with(csrf()).cookie(member))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/blogs/open-blog").cookie(member)).andExpect(jsonPath("$.myRole").value("OWNER"));
        mvc.perform(get("/api/blogs/open-blog").cookie(owner)).andExpect(jsonPath("$.myRole").value("MEMBER"));
        assertThat(notifications("owner@example.com", "TRANSFER_RESULT")).isEqualTo(1);
    }

    @Test
    void 폐쇄를_예약하고_철회하고_배치가_폐쇄한다() throws Exception {
        mvc.perform(post("/api/blogs/open-blog/transfer").with(csrf()).cookie(owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"toUserId\":" + memberId() + "}"));
        mvc.perform(post("/api/blogs/open-blog/close").with(csrf()).cookie(member)).andExpect(status().isForbidden());
        mvc.perform(post("/api/blogs/open-blog/close").with(csrf()).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closeScheduledAt").exists());
        assertThat(notifications("member@example.com", "BLOG_CLOSING")).isEqualTo(1);
        assertThat(notifications("sub@example.com", "BLOG_CLOSING")).isEqualTo(1);
        assertThat(notifications("owner@example.com", "BLOG_CLOSING")).isZero();
        // 대기 중인 위임 요청은 취소되고, 폐쇄 예정 중에는 위임할 수 없다
        assertThat(jdbc.queryForObject("SELECT status FROM blog_transfer_requests", String.class)).isEqualTo("CANCELED");
        mvc.perform(post("/api/blogs/open-blog/transfer").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"toUserId\":" + memberId() + "}"))
                .andExpect(status().isConflict());
        // 폐쇄 예정 중에도 평소처럼 글을 쓴다 (D-67)
        writePost(member, "open-blog", "폐쇄 전 글", "본문");

        mvc.perform(delete("/api/blogs/open-blog/close").with(csrf()).cookie(owner)).andExpect(status().isNoContent());
        assertThat(notifications("member@example.com", "BLOG_CLOSE_CANCELED")).isEqualTo(1);
        mvc.perform(get("/api/blogs/open-blog")).andExpect(jsonPath("$.status").value("ACTIVE"));

        mvc.perform(post("/api/blogs/open-blog/close").with(csrf()).cookie(owner)).andExpect(status().isOk());
        assertThat(notifications("member@example.com", "BLOG_CLOSING")).isEqualTo(2);
        LocalDateTime scheduled = jdbc.queryForObject("SELECT close_scheduled_at FROM blogs WHERE slug = 'open-blog'",
                LocalDateTime.class);

        // 3일 전 알림, 그리고 예정 시각이 지나면 폐쇄
        dailyBatch.runAll(scheduled.minusDays(3));
        assertThat(notifications("member@example.com", "BLOG_CLOSING")).isEqualTo(3);
        dailyBatch.runAll(scheduled.minusDays(3));
        assertThat(notifications("member@example.com", "BLOG_CLOSING")).isEqualTo(3);
        dailyBatch.runAll(scheduled.plusMinutes(1));
        mvc.perform(get("/api/blogs/open-blog")).andExpect(status().isNotFound());

        // 30일 뒤 내용을 지우고 주소를 비운다 (D-86)
        dailyBatch.runAll(scheduled.plusDays(31));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blogs WHERE slug = 'open-blog'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blogs WHERE deleted_at IS NOT NULL", Integer.class)).isEqualTo(1);
    }

    @Test
    void 배치는_30일_지난_삭제_글과_만료_토큰을_지운다() throws Exception {
        Long postId = writePost(member, "open-blog", "지울 글", "본문");
        mvc.perform(post("/api/posts/" + postId + "/comments").with(csrf()).cookie(owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"댓글\"}"));
        mvc.perform(delete("/api/posts/" + postId).with(csrf()).cookie(member)).andExpect(status().isNoContent());
        LocalDateTime now = LocalDateTime.now();
        dailyBatch.runAll(now.plusDays(29));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE id = ?", Integer.class, postId)).isEqualTo(1);
        dailyBatch.runAll(now.plusDays(31));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE id = ?", Integer.class, postId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments", Integer.class)).isZero();
        // 30분 무활동 로그인은 만료되어 지워진다
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens", Integer.class)).isZero();
    }

    @Test
    void 회원탈퇴는_블로그장이면_막고_멤버는_탈퇴한_회원이_된다() throws Exception {
        Long postId = writePost(member, "open-blog", "멤버 글", "본문");
        mvc.perform(post("/api/me/withdrawal").with(csrf()).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWNED_BLOGS_REMAIN"));
        mvc.perform(post("/api/me/withdrawal").with(csrf()).cookie(member).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Wrong123!\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/me/withdrawal").with(csrf()).cookie(member).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/me").cookie(member)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.authorName").value("탈퇴한 회원"));
        mvc.perform(get("/api/blogs/open-blog")).andExpect(jsonPath("$.memberCount").value(1));
        // 같은 이메일로 바로 다시 가입할 수 있다 (6.5)
        // (가입 인증번호는 1분에 한 번만 다시 받을 수 있어, 앞서 쓴 번호 기록을 지우고 다시 가입한다)
        jdbc.update("DELETE FROM verification_codes");
        signUp("member@example.com", "새멤버");

        // 30일 뒤 개인정보 삭제
        dailyBatch.runAll(LocalDateTime.now().plusDays(31));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE status = 'WITHDRAWN' AND nickname IS NULL AND name IS NULL",
                Integer.class)).isEqualTo(1);
    }
}
