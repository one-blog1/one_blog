package com.oneblog.blog.join;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.oneblog.blog.BlogTestSupport;

import jakarta.servlet.http.Cookie;

/** 블로그 참여 신청·승인 (BLG-04, BLG-05, 6.5 / specs/003-blog-join/quickstart). */
class BlogJoinIntegrationTest extends BlogTestSupport {

    private Cookie owner;
    private Cookie alice;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUpAndLogin("owner@example.com", "블로그장");
        alice = signUpAndLogin("alice@example.com", "앨리스");
        createBlog(owner, "open-blog", "PUBLIC", "OPEN").andExpect(status().isCreated());
        createBlog(owner, "approval-blog", "PUBLIC", "APPROVAL").andExpect(status().isCreated());
    }

    private ResultActions apply(Cookie who, String slug) throws Exception {
        return mvc.perform(post("/api/blogs/" + slug + "/join").with(csrf()).cookie(who));
    }

    private Long pendingId(String slug) {
        return jdbc.queryForObject("SELECT id FROM blog_join_requests WHERE blog_id = ? AND status = 'PENDING'",
                Long.class, blogId(slug));
    }

    private int memberCount(String slug) {
        return jdbc.queryForObject("SELECT member_count FROM blogs WHERE slug = ?", Integer.class, slug);
    }

    @Test
    void 자유_참여_블로그는_바로_멤버가_된다() throws Exception {
        apply(alice, "open-blog")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("MEMBER"));
        assertThat(memberCount("open-blog")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT role FROM blog_members WHERE blog_id = ? AND user_id = ?", String.class,
                blogId("open-blog"), userId("alice@example.com"))).isEqualTo("MEMBER");
        mvc.perform(get("/api/me/blogs").cookie(alice))
                .andExpect(jsonPath("$.joined[0].slug").value("open-blog"))
                .andExpect(jsonPath("$.joined[0].role").value("MEMBER"));

        apply(alice, "open-blog")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
        apply(owner, "open-blog").andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
    }

    @Test
    void 승인제는_신청이_대기되고_블로그장이_승인하면_멤버가_된다() throws Exception {
        apply(alice, "approval-blog")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(memberCount("approval-blog")).isEqualTo(1);
        mvc.perform(get("/api/blogs/approval-blog/join").cookie(alice))
                .andExpect(jsonPath("$.status").value("PENDING"));
        apply(alice, "approval-blog")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOIN_REQUEST_PENDING"));

        mvc.perform(get("/api/blogs/approval-blog/join-requests").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nickname").value("앨리스"));

        Long id = pendingId("approval-blog");
        mvc.perform(post("/api/blogs/approval-blog/join-requests/" + id + "/approve").with(csrf()).cookie(owner))
                .andExpect(status().isNoContent());
        assertThat(memberCount("approval-blog")).isEqualTo(2);
        mvc.perform(get("/api/blogs/approval-blog/join").cookie(alice))
                .andExpect(jsonPath("$.status").value("MEMBER"));
        // 같은 신청을 다시 처리할 수 없다
        mvc.perform(post("/api/blogs/approval-blog/join-requests/" + id + "/reject").with(csrf()).cookie(owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOIN_REQUEST_ALREADY_PROCESSED"));
    }

    @Test
    void 거절되면_7일_뒤에_다시_신청할_수_있다() throws Exception {
        apply(alice, "approval-blog").andExpect(status().isAccepted());
        mvc.perform(post("/api/blogs/approval-blog/join-requests/" + pendingId("approval-blog") + "/reject")
                        .with(csrf()).cookie(owner))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/blogs/approval-blog/join").cookie(alice))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.retryAt").exists());
        apply(alice, "approval-blog")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REAPPLY_TOO_SOON"));

        jdbc.update("UPDATE blog_join_requests SET processed_at = DATE_SUB(NOW(6), INTERVAL 8 DAY)");
        apply(alice, "approval-blog").andExpect(status().isAccepted());
        assertThat(memberCount("approval-blog")).isEqualTo(1);
    }

    @Test
    void 대기_중인_신청은_본인이_취소할_수_있다() throws Exception {
        apply(alice, "approval-blog").andExpect(status().isAccepted());
        mvc.perform(delete("/api/blogs/approval-blog/join").with(csrf()).cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NONE"));
        assertThat(jdbc.queryForObject("SELECT status FROM blog_join_requests", String.class)).isEqualTo("CANCELED");
        mvc.perform(delete("/api/blogs/approval-blog/join").with(csrf()).cookie(alice))
                .andExpect(status().isNotFound());
        // 취소한 뒤에는 바로 다시 신청할 수 있다
        apply(alice, "approval-blog").andExpect(status().isAccepted());
    }

    @Test
    void 멤버_관리_권한이_없으면_신청을_보거나_처리할_수_없다() throws Exception {
        apply(alice, "approval-blog").andExpect(status().isAccepted());
        Long id = pendingId("approval-blog");
        Cookie bob = signUpAndLogin("bob@example.com", "밥아저씨");

        mvc.perform(get("/api/blogs/approval-blog/join-requests").cookie(bob)).andExpect(status().isForbidden());
        mvc.perform(post("/api/blogs/approval-blog/join-requests/" + id + "/approve").with(csrf()).cookie(alice))
                .andExpect(status().isForbidden());

        // 권한 없는 부블로그장도 안 되고, 멤버 관리 권한을 받은 부블로그장은 된다 (D-71)
        addMember("approval-blog", "bob@example.com", "SUB_OWNER");
        mvc.perform(post("/api/blogs/approval-blog/join-requests/" + id + "/approve").with(csrf()).cookie(bob))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE blog_members SET can_manage_members = 1 WHERE user_id = ?", userId("bob@example.com"));
        mvc.perform(post("/api/blogs/approval-blog/join-requests/" + id + "/approve").with(csrf()).cookie(bob))
                .andExpect(status().isNoContent());

        // 다른 블로그의 신청 번호로는 처리할 수 없다
        createBlog(alice, "alice-blog", "PUBLIC", "APPROVAL").andExpect(status().isCreated());
        apply(bob, "alice-blog").andExpect(status().isAccepted());
        Long other = pendingId("alice-blog");
        mvc.perform(post("/api/blogs/approval-blog/join-requests/" + other + "/approve").with(csrf()).cookie(owner))
                .andExpect(status().isNotFound());
    }

    @Test
    void 비공개는_신청할_수_없고_일부_공개는_공유_링크가_필요하다() throws Exception {
        createBlog(owner, "secret-blog", "PRIVATE", "OPEN").andExpect(status().isCreated());
        createBlog(owner, "link-blog", "UNLISTED", "OPEN").andExpect(status().isCreated());
        String key = jdbc.queryForObject("SELECT share_token FROM blogs WHERE slug = 'link-blog'", String.class);

        apply(alice, "secret-blog")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIVATE_BLOG"));
        apply(alice, "link-blog")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LINK_REQUIRED"));
        mvc.perform(post("/api/blogs/link-blog/join").param("key", key).with(csrf()).cookie(alice))
                .andExpect(status().isCreated());
        // 멤버가 되면 공유 링크 없이도 볼 수 있다
        mvc.perform(get("/api/blogs/link-blog").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("MEMBER"));
    }

    @Test
    void 떠났던_회원은_같은_멤버십을_되살린다() throws Exception {
        apply(alice, "open-blog").andExpect(status().isCreated());
        jdbc.update("UPDATE blog_members SET status = 'LEFT', left_at = NOW(6) WHERE user_id = ?",
                userId("alice@example.com"));
        jdbc.update("UPDATE blogs SET member_count = 1 WHERE slug = 'open-blog'");

        apply(alice, "open-blog").andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_members WHERE user_id = ?", Integer.class,
                userId("alice@example.com"))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM blog_members WHERE user_id = ?", String.class,
                userId("alice@example.com"))).isEqualTo("ACTIVE");
        assertThat(memberCount("open-blog")).isEqualTo(2);
    }

    @Test
    void 강제_퇴장된_회원은_다시_참여할_수_없다() throws Exception {
        apply(alice, "open-blog").andExpect(status().isCreated());
        // 강제 퇴장은 블랙리스트에 올리고(013, BLG-11), 블랙리스트에 걸리면 다시 참여할 수 없다
        mvc.perform(post("/api/blogs/open-blog/members/" + userId("alice@example.com") + "/sanctions").with(csrf())
                        .cookie(owner).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"KICK\",\"reason\":\"규칙 위반\"}"))
                .andExpect(status().isNoContent());
        apply(alice, "open-blog")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BLACKLISTED"));
    }

    @Test
    void 로그인하지_않았거나_관리자면_신청할_수_없다() throws Exception {
        mvc.perform(post("/api/blogs/open-blog/join").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/blogs/open-blog/join")).andExpect(status().isUnauthorized());
        makeAdmin("alice@example.com", "admin01");
        apply(alice, "open-blog")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_NOT_ALLOWED"));
        assertThat(memberCount("open-blog")).isEqualTo(1);
    }
}
