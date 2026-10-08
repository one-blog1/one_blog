package com.oneblog.social;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.oneblog.blog.BlogTestSupport;

import jakarta.servlet.http.Cookie;

/** 팔로우·블로그 구독·프로필 (SOC-01~03, D-02, D-50 / specs/009-social-profile). */
class SocialIntegrationTest extends BlogTestSupport {

    private Cookie alice;
    private Cookie bob;

    @BeforeEach
    void setUp() throws Exception {
        alice = signUpAndLogin("alice@example.com", "앨리스");
        bob = signUpAndLogin("bob@example.com", "밥아저씨");
    }

    @Test
    void 팔로우를_누르면_팔로우되고_다시_누르면_취소된다() throws Exception {
        mvc.perform(post("/api/users/밥아저씨/follow").with(csrf()).cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.followerCount").value(1));
        mvc.perform(get("/api/users/밥아저씨/followers"))
                .andExpect(jsonPath("$.items[*].nickname", contains("앨리스")));
        mvc.perform(get("/api/users/앨리스/following"))
                .andExpect(jsonPath("$.items[*].nickname", contains("밥아저씨")));
        mvc.perform(get("/api/users/밥아저씨").cookie(alice))
                .andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.followerCount").value(1))
                .andExpect(jsonPath("$.me").value(false));

        mvc.perform(post("/api/users/밥아저씨/follow").with(csrf()).cookie(alice))
                .andExpect(jsonPath("$.following").value(false))
                .andExpect(jsonPath("$.followerCount").value(0));
    }

    @Test
    void 프로필_공개_범위를_끄면_남에게_블로그와_팔로워_목록이_숨고_팔로우를_받지_않는다() throws Exception {
        createBlog(bob, "bob-public", "PUBLIC").andExpect(status().isCreated());
        mvc.perform(get("/api/me/privacy").cookie(bob))
                .andExpect(jsonPath("$.showBlogs").value(true))
                .andExpect(jsonPath("$.allowFollow").value(true));
        mvc.perform(put("/api/me/privacy").with(csrf()).cookie(bob)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"showBlogs\":false,\"showFollows\":false,\"allowFollow\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showFollows").value(false));

        mvc.perform(get("/api/users/밥아저씨").cookie(alice))
                .andExpect(jsonPath("$.ownedBlogs", hasSize(0)))
                .andExpect(jsonPath("$.blogsHidden").value(true))
                .andExpect(jsonPath("$.followsHidden").value(true))
                .andExpect(jsonPath("$.followAllowed").value(false));
        // 본인에게는 그대로 보인다
        mvc.perform(get("/api/users/밥아저씨").cookie(bob)).andExpect(jsonPath("$.ownedBlogs", hasSize(1)));
        mvc.perform(get("/api/users/밥아저씨/followers").cookie(alice)).andExpect(status().isForbidden());
        mvc.perform(get("/api/users/밥아저씨/followers").cookie(bob)).andExpect(status().isOk());
        mvc.perform(post("/api/users/밥아저씨/follow").with(csrf()).cookie(alice))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FOLLOW_NOT_ALLOWED"));
        // 보내지 않은 항목은 그대로
        mvc.perform(put("/api/me/privacy").with(csrf()).cookie(bob)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"allowFollow\":true}"))
                .andExpect(jsonPath("$.showBlogs").value(false))
                .andExpect(jsonPath("$.allowFollow").value(true));
        mvc.perform(post("/api/users/밥아저씨/follow").with(csrf()).cookie(alice)).andExpect(status().isOk());
    }

    @Test
    void 자기_자신과_없는_회원은_팔로우할_수_없다() throws Exception {
        mvc.perform(post("/api/users/앨리스/follow").with(csrf()).cookie(alice))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_FOLLOW_SELF"));
        mvc.perform(post("/api/users/없는사람/follow").with(csrf()).cookie(alice))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/users/밥아저씨/follow").with(csrf()))
                .andExpect(status().isUnauthorized());
        Cookie admin = createAdminAndLogin("admin01");
        mvc.perform(post("/api/users/밥아저씨/follow").with(csrf()).cookie(admin))
                .andExpect(status().isForbidden());
    }

    @Test
    void 프로필은_다른_사람에게_전체_공개_블로그만_보인다() throws Exception {
        createBlog(bob, "bob-public", "PUBLIC").andExpect(status().isCreated());
        createBlog(bob, "bob-secret", "PRIVATE").andExpect(status().isCreated());
        createBlog(alice, "alice-blog", "PUBLIC").andExpect(status().isCreated());
        addMember("alice-blog", "bob@example.com", "MEMBER");

        mvc.perform(get("/api/users/밥아저씨"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownedBlogs[*].slug", contains("bob-public")))
                .andExpect(jsonPath("$.joinedBlogs[*].slug", contains("alice-blog")));
        mvc.perform(get("/api/users/밥아저씨").cookie(bob))
                .andExpect(jsonPath("$.me").value(true))
                .andExpect(jsonPath("$.ownedBlogs", hasSize(2)));
        mvc.perform(get("/users/밥아저씨")).andExpect(forwardedUrl("/profile.html"));
        mvc.perform(get("/api/users/없는사람")).andExpect(status().isNotFound());
    }

    @Test
    void 일부_공개_블로그는_링크로_구독하면_링크_없이_들어온다() throws Exception {
        String body = createBlog(alice, "link-blog", "UNLISTED").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String shareUrl = JsonPath.read(body, "$.shareUrl");
        String key = shareUrl.substring(shareUrl.indexOf("key=") + 4);

        mvc.perform(get("/api/blogs/link-blog").cookie(bob)).andExpect(status().isForbidden());
        // 링크 없이는 구독도 할 수 없다
        mvc.perform(post("/api/blogs/link-blog/subscription").with(csrf()).cookie(bob))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/blogs/link-blog/subscription").param("key", key).with(csrf()).cookie(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(true))
                .andExpect(jsonPath("$.subscriberCount").value(1));

        mvc.perform(get("/api/blogs/link-blog").cookie(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(true));
        mvc.perform(get("/api/me/subscriptions").cookie(bob))
                .andExpect(jsonPath("$[*].slug", contains("link-blog")));

        // 취소하면 다시 링크가 필요하다
        mvc.perform(post("/api/blogs/link-blog/subscription").with(csrf()).cookie(bob))
                .andExpect(jsonPath("$.subscribed").value(false));
        mvc.perform(get("/api/blogs/link-blog").cookie(bob)).andExpect(status().isForbidden());
    }

    @Test
    void 비공개로_바뀐_블로그도_구독을_끊을_수_있다() throws Exception {
        createBlog(alice, "open-blog", "PUBLIC").andExpect(status().isCreated());
        mvc.perform(post("/api/blogs/open-blog/subscription").with(csrf()).cookie(bob))
                .andExpect(jsonPath("$.subscribed").value(true));
        jdbc.update("UPDATE blogs SET visibility = 'PRIVATE' WHERE slug = 'open-blog'");
        // 멤버가 아닌 구독자는 글을 볼 수 없다 (D-37)
        mvc.perform(get("/api/blogs/open-blog").cookie(bob)).andExpect(status().isForbidden());
        mvc.perform(post("/api/blogs/open-blog/subscription").with(csrf()).cookie(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscribed").value(false));
    }
}
