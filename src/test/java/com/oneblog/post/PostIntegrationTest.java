package com.oneblog.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import jakarta.servlet.http.Cookie;

/** 글 쓰기·보기·고치기·지우기와 목록 (BRD-01, BRD-02, 6.6, D-07, D-33, D-58 / specs/004-posts). */
class PostIntegrationTest extends PostTestSupport {

    private Cookie owner;
    private Cookie member;
    private Cookie stranger;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUpAndLogin("owner@example.com", "블로그장");
        member = signUpAndLogin("member@example.com", "멤버회원");
        stranger = signUpAndLogin("stranger@example.com", "지나가는이");
        createBlog(owner, "open-blog", "PUBLIC").andExpect(status().isCreated());
        addMember("open-blog", "member@example.com", "MEMBER");
    }

    @Test
    void 멤버는_글을_쓰고_누구나_읽는다() throws Exception {
        Long id = writePost(member, "open-blog", "첫 글", "# 제목\\n\\n**굵게** 본문");
        mvc.perform(get("/api/posts/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("첫 글"))
                .andExpect(jsonPath("$.authorName").value("멤버회원"))
                .andExpect(jsonPath("$.contentHtml").value(org.hamcrest.Matchers.containsString("<strong>굵게</strong>")))
                .andExpect(jsonPath("$.content").doesNotExist())
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.blogSlug").value("open-blog"));
        mvc.perform(get("/api/posts/" + id).cookie(member))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.canDelete").value(true))
                .andExpect(jsonPath("$.content").exists());
        mvc.perform(get("/blog/open-blog/posts/" + id))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("id=\"post\"")));
    }

    @Test
    void 멤버가_아니면_글을_쓸_수_없다() throws Exception {
        writePost(stranger, "open-blog", """
                {"title":"남의 블로그","content":"본문"}
                """).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/blogs/open-blog/posts")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"a\",\"content\":\"b\"}"))
                .andExpect(status().isUnauthorized());
        makeAdmin("stranger@example.com", "admin01");
        writePost(stranger, "open-blog", "{\"title\":\"a\",\"content\":\"b\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_NOT_ALLOWED"));
    }

    @Test
    void 제목과_본문_길이를_검사한다() throws Exception {
        writePost(member, "open-blog", "{\"title\":\"  \",\"content\":\" \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'title')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'content')]").exists());
        writePost(member, "open-blog", "{\"title\":\"" + "가".repeat(31) + "\",\"content\":\"본문\"}")
                .andExpect(status().isBadRequest());
        writePost(member, "open-blog", "{\"title\":\"길이\",\"content\":\"" + "가".repeat(5001) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("content"));
        // 5,000자는 된다. 이모지는 한 글자로 센다
        writePost(member, "open-blog", "{\"title\":\"딱 맞음\",\"content\":\"" + "😀".repeat(5000) + "\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void 본문의_스크립트와_위험한_링크는_지운다() throws Exception {
        Long id = writePost(member, "open-blog", "XSS",
                "<script>alert(1)</script> [링크](javascript:alert(1)) <img src=x onerror=alert(1)> [좋은링크](https://example.com)");
        String html = com.jayway.jsonpath.JsonPath.read(mvc.perform(get("/api/posts/" + id))
                .andReturn().getResponse().getContentAsString(), "$.contentHtml");
        // 마크다운 안의 HTML은 실행되지 않는 글자(&lt;...&gt;)로 바뀐다
        assertThat(html).doesNotContain("<script").doesNotContain("javascript:").doesNotContain("<img")
                .doesNotContain("onerror=alert(1)>");
        assertThat(html).contains("href=\"https://example.com\"").contains("noopener");
    }

    @Test
    void 글은_작성자만_고치고_블로그장은_지울_수만_있다() throws Exception {
        Long id = writePost(member, "open-blog", "원래 제목", "원래 본문");
        mvc.perform(put("/api/posts/" + id).with(csrf()).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"블로그장이 고침\",\"content\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/posts/" + id).with(csrf()).cookie(member).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"고친 제목\",\"content\":\"고친 본문\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT title FROM posts WHERE id = ?", String.class, id)).isEqualTo("고친 제목");

        mvc.perform(delete("/api/posts/" + id).with(csrf()).cookie(stranger)).andExpect(status().isForbidden());
        mvc.perform(get("/api/posts/" + id).cookie(owner))
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.canDelete").value(true));
        mvc.perform(delete("/api/posts/" + id).with(csrf()).cookie(owner)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT deleted_by FROM posts WHERE id = ?", String.class, id))
                .isEqualTo("BLOG_OWNER");
        mvc.perform(get("/api/posts/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void 글_관리_권한이_없는_부블로그장은_남의_글을_지울_수_없다() throws Exception {
        Long id = writePost(member, "open-blog", "멤버 글", "본문");
        signUpAndLogin("sub@example.com", "부블로그장");
        Cookie sub = loginCookie("sub@example.com");
        addMember("open-blog", "sub@example.com", "SUB_OWNER");
        mvc.perform(delete("/api/posts/" + id).with(csrf()).cookie(sub)).andExpect(status().isForbidden());
        jdbc.update("UPDATE blog_members SET can_manage_posts = 1 WHERE user_id = ?", userId("sub@example.com"));
        mvc.perform(delete("/api/posts/" + id).with(csrf()).cookie(sub)).andExpect(status().isNoContent());
    }

    @Test
    void 작성자가_블로그를_떠나면_탈퇴한_계정으로_보이고_고칠_수_없다() throws Exception {
        Long id = writePost(member, "open-blog", "떠날 사람", "본문");
        jdbc.update("UPDATE blog_members SET status = 'LEFT' WHERE user_id = ?", userId("member@example.com"));
        jdbc.update("UPDATE posts SET author_detached = 1 WHERE id = ?", id);
        mvc.perform(get("/api/posts/" + id).cookie(member))
                .andExpect(jsonPath("$.authorName").value("탈퇴한 계정"))
                .andExpect(jsonPath("$.canEdit").value(false));
        mvc.perform(put("/api/posts/" + id).with(csrf()).cookie(member).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"content\":\"y\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 공지는_글_관리_권한이_있어야_쓰고_목록_위에_따로_나온다() throws Exception {
        writePost(member, "open-blog", "{\"title\":\"공지 시도\",\"content\":\"x\",\"notice\":true}")
                .andExpect(status().isForbidden());
        writePost(owner, "open-blog", "{\"title\":\"블로그 공지\",\"content\":\"x\",\"notice\":true}")
                .andExpect(status().isCreated());
        for (int i = 1; i <= 12; i++) {
            writePost(member, "open-blog", "글 " + i, "본문 " + i);
        }
        mvc.perform(get("/api/blogs/open-blog/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notices.length()").value(1))
                .andExpect(jsonPath("$.notices[0].notice").value(true))
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.items[0].title").value("글 12"))
                .andExpect(jsonPath("$.totalItems").value(12))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/blogs/open-blog/posts").param("page", "2"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[1].title").value("글 1"));
        mvc.perform(get("/api/blogs/open-blog/posts").param("size", "20"))
                .andExpect(jsonPath("$.items.length()").value(12));
    }

    @Test
    void 비공개_블로그의_글은_멤버만_본다() throws Exception {
        createBlog(owner, "secret-blog", "PRIVATE").andExpect(status().isCreated());
        Long id = writePost(owner, "secret-blog", "비밀 글", "비밀 본문");
        mvc.perform(get("/api/posts/" + id)).andExpect(status().isForbidden());
        mvc.perform(get("/api/posts/" + id).cookie(stranger)).andExpect(status().isForbidden());
        mvc.perform(get("/api/blogs/secret-blog/posts").cookie(stranger)).andExpect(status().isForbidden());
        mvc.perform(get("/api/posts/" + id).cookie(owner)).andExpect(status().isOk());
    }
}
