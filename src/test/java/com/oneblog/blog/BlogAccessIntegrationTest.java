package com.oneblog.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import jakarta.servlet.http.Cookie;

/**
 * 공개 범위에 따른 블로그 첫 화면 접근 (BLG-01, SEC-07, D-49, D-50 / quickstart 17~20, SC-004).
 * 볼 수 없는 사람의 응답에는 블로그 이름·소개·태그가 하나도 없어야 한다.
 */
class BlogAccessIntegrationTest extends BlogTestSupport {

    private static final String SECRET_NAME = "비밀블로그이름";

    private Cookie owner;
    private Cookie stranger;
    private String shareKey;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUpAndLogin("owner@example.com", "주인장");
        stranger = signUpAndLogin("stranger@example.com", "지나가는사람");
        postBlog(owner, """
                {"name":"공개 블로그","slug":"open-blog","description":"누구나","visibility":"PUBLIC","joinPolicy":"OPEN",
                 "tags":["공개"]}
                """).andExpect(status().isCreated());
        postBlog(owner, """
                {"name":"%s","slug":"link-blog","description":"비밀 소개","visibility":"UNLISTED","joinPolicy":"OPEN",
                 "tags":["비밀태그"]}
                """.formatted(SECRET_NAME)).andExpect(status().isCreated());
        postBlog(owner, """
                {"name":"%s","slug":"secret-blog","description":"비밀 소개","visibility":"PRIVATE","joinPolicy":"OPEN",
                 "tags":["비밀태그"]}
                """.formatted(SECRET_NAME)).andExpect(status().isCreated());
        shareKey = jdbc.queryForObject("SELECT share_token FROM blogs WHERE slug = 'link-blog'", String.class);
    }

    private String body(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        return mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    private void assertNoBlogInfo(String body) {
        assertThat(body).doesNotContain(SECRET_NAME).doesNotContain("비밀 소개").doesNotContain("비밀태그")
                .doesNotContain("주인장").doesNotContain(shareKey);
    }

    @Test
    void 공개_블로그는_누구나_본다() throws Exception {
        mvc.perform(get("/api/blogs/open-blog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("공개 블로그"))
                .andExpect(jsonPath("$.tags[0]").value("공개"))
                .andExpect(jsonPath("$.ownerNickname").value("주인장"))
                .andExpect(jsonPath("$.myRole").doesNotExist())
                .andExpect(jsonPath("$.shareUrl").doesNotExist());
        mvc.perform(get("/api/blogs/OPEN-BLOG").cookie(stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").doesNotExist());
        mvc.perform(get("/api/blogs/open-blog").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.shareUrl").doesNotExist());
    }

    @Test
    void 일부_공개는_링크가_없으면_안내만_보인다() throws Exception {
        String anonymous = body(get("/api/blogs/link-blog"), 403);
        assertThat(anonymous).contains("LINK_REQUIRED");
        assertNoBlogInfo(anonymous);

        String member = body(get("/api/blogs/link-blog").cookie(stranger), 403);
        assertNoBlogInfo(member);

        String wrongKey = body(get("/api/blogs/link-blog").param("key", shareKey.substring(1) + "0"), 403);
        assertThat(wrongKey).isEqualTo(anonymous);
        String shortKey = body(get("/api/blogs/link-blog").param("key", "abc"), 403);
        assertThat(shortKey).isEqualTo(anonymous);
    }

    @Test
    void 일부_공개는_공유_링크로_누구나_보고_공유_링크는_멤버에게만_준다() throws Exception {
        mvc.perform(get("/api/blogs/link-blog").param("key", shareKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(SECRET_NAME))
                .andExpect(jsonPath("$.myRole").doesNotExist())
                .andExpect(jsonPath("$.shareUrl").doesNotExist());
        mvc.perform(get("/api/blogs/link-blog").param("key", shareKey).cookie(stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareUrl").doesNotExist());
        // 링크로 들어와도 자동 구독·멤버 등록이 생기지 않는다 (D-50)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_members", Integer.class)).isEqualTo(3);

        mvc.perform(get("/api/blogs/link-blog").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.shareUrl").value("/blog/link-blog?key=" + shareKey));
    }

    @Test
    void 비공개는_멤버만_본다() throws Exception {
        String anonymous = body(get("/api/blogs/secret-blog"), 403);
        assertThat(anonymous).contains("PRIVATE_BLOG");
        assertNoBlogInfo(anonymous);
        assertNoBlogInfo(body(get("/api/blogs/secret-blog").cookie(stranger), 403));
        // 비공개 블로그에는 공유 링크가 없어서 key를 붙여도 볼 수 없다
        assertNoBlogInfo(body(get("/api/blogs/secret-blog").param("key", shareKey), 403));

        mvc.perform(get("/api/blogs/secret-blog").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(SECRET_NAME))
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.shareUrl").doesNotExist());
    }

    @Test
    void 멤버가_떠나면_비공개_블로그를_볼_수_없다() throws Exception {
        jdbc.update("""
                UPDATE blog_members SET status = 'LEFT' WHERE user_id =
                (SELECT id FROM users WHERE email = 'owner@example.com')""");
        assertNoBlogInfo(body(get("/api/blogs/secret-blog").cookie(owner), 403));
    }

    @Test
    void 없거나_폐쇄된_블로그는_404() throws Exception {
        mvc.perform(get("/api/blogs/no-such-blog"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BLOG_NOT_FOUND"));
        mvc.perform(get("/api/blogs/a_b")).andExpect(status().isNotFound());
        jdbc.update("UPDATE blogs SET status = 'CLOSED' WHERE slug = 'open-blog'");
        mvc.perform(get("/api/blogs/open-blog")).andExpect(status().isNotFound());
        mvc.perform(get("/api/blogs")).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void 블로그_화면_HTML에는_블로그_정보가_없다() throws Exception {
        mvc.perform(get("/blog/secret-blog"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .forwardedUrl("/blog.html"));
    }
}
