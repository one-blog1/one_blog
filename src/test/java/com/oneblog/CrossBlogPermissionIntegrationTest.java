package com.oneblog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;
import com.oneblog.post.PostTestSupport;

import jakarta.servlet.http.Cookie;

/**
 * 다른 블로그의 블로그장이 남의 블로그를 관리할 수 없다 (SEC-07, SEC-11, 2장 권한표, 029 US2).
 * 지금까지 거부 테스트는 블로그가 없는 회원으로만 했다. 여기서는 "자기 블로그에서는 블로그장"인 회원이
 * 다른 블로그에 손대거나, 다른 블로그의 번호를 내 블로그 주소에 섞어 넣는 경우를 본다.
 */
class CrossBlogPermissionIntegrationTest extends PostTestSupport {

    private Cookie ownerA;
    private Cookie ownerB;
    private Cookie memberB;
    private Long postA;
    private Long categoryA;
    private Long categoryB;

    @BeforeEach
    void setUp() throws Exception {
        ownerA = signUpAndLogin("a@example.com", "에이장", "김에이", "010-3333-0001");
        Cookie memberA = signUpAndLogin("ma@example.com", "에이멤버", "이에이", "010-3333-0002");
        ownerB = signUpAndLogin("b@example.com", "비장", "박비", "010-3333-0003");
        memberB = signUpAndLogin("mb@example.com", "비멤버", "최비", "010-3333-0004");
        createBlog(ownerA, "blog-a", "PUBLIC").andExpect(status().isCreated());
        createBlog(ownerB, "blog-b", "PUBLIC").andExpect(status().isCreated());
        addMember("blog-a", "ma@example.com", "MEMBER");
        addMember("blog-b", "mb@example.com", "MEMBER");
        postA = writePost(memberA, "blog-a", "에이의 글", "본문");
        categoryA = category(ownerA, "blog-a", "에이분류");
        categoryB = category(ownerB, "blog-b", "비분류");
    }

    private Long category(Cookie login, String slug, String name) throws Exception {
        String body = mvc.perform(post("/api/blogs/" + slug + "/categories").with(csrf()).cookie(login)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void 다른_블로그의_블로그장은_내_블로그를_관리할_수_없다() throws Exception {
        mvc.perform(delete("/api/posts/" + postA).with(csrf()).cookie(ownerB))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/blogs/blog-a/categories").with(csrf()).cookie(ownerB)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"몰래\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(put("/api/categories/" + categoryA).with(csrf()).cookie(ownerB)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"바꿈\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(delete("/api/categories/" + categoryA).with(csrf()).cookie(ownerB))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/blogs/blog-a/members/" + userId("ma@example.com") + "/sanctions").with(csrf())
                        .cookie(ownerB).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"WARNING\",\"reason\":\"몰래\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(put("/api/blogs/blog-a/info").with(csrf()).cookie(ownerB)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"몰래 바꿈\",\"tags\":[]}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/blogs/blog-a/close").with(csrf()).cookie(ownerB))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 아무것도 바뀌지 않았다
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM posts WHERE id = ?", Boolean.class, postA)).isTrue();
        assertThat(jdbc.queryForObject("SELECT name FROM categories WHERE id = ?", String.class, categoryA))
                .isEqualTo("에이분류");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE blog_id = ?", Integer.class,
                blogId("blog-a"))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sanctions", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM blogs WHERE slug = 'blog-a'", String.class))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT description FROM blogs WHERE slug = 'blog-a'", String.class)).isNull();
    }

    @Test
    void 내_블로그_주소에_다른_블로그의_번호를_넣어도_처리되지_않는다() throws Exception {
        // B 블로그에서 생긴 신고와 블랙리스트 해제 문의
        Long postB = writePost(memberB, "blog-b", "비의 글", "본문");
        Cookie reporter = signUpAndLogin("r@example.com", "신고자", "정신고", "010-3333-0005");
        mvc.perform(post("/api/reports").with(csrf()).cookie(reporter).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":" + postB + ",\"reason\":\"SPAM\"}"))
                .andExpect(status().isCreated());
        Long reportB = jdbc.queryForObject("SELECT id FROM reports", Long.class);
        mvc.perform(post("/api/blogs/blog-b/members/" + userId("mb@example.com") + "/sanctions").with(csrf())
                        .cookie(ownerB).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"KICK\",\"reason\":\"규칙 위반\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/blogs/blog-b/blacklist-inquiries").with(csrf()).cookie(memberB)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"풀어 주세요\"}"))
                .andExpect(status().isCreated());
        Long inquiryB = jdbc.queryForObject("SELECT id FROM blacklist_inquiries", Long.class);

        // A의 블로그장이 A 주소로 B의 신고·문의·멤버를 처리하려 하면 없는 것으로 본다
        mvc.perform(post("/api/blogs/blog-a/reports/" + reportB + "/resolve").with(csrf()).cookie(ownerA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"NO_ISSUE\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/blogs/blog-a/blacklist-inquiries/" + inquiryB + "/release").with(csrf())
                        .cookie(ownerA))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INQUIRY_NOT_FOUND"));
        mvc.perform(post("/api/blogs/blog-a/members/" + userId("b@example.com") + "/sanctions").with(csrf())
                        .cookie(ownerA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"WARNING\",\"reason\":\"남의 블로그장\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        // 다른 블로그의 카테고리는 번호로 고칠 수 없다
        mvc.perform(put("/api/categories/" + categoryB).with(csrf()).cookie(ownerA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"바꿈\"}"))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT status FROM reports", String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM blacklist_inquiries", String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sanctions WHERE blog_id = ?", Integer.class,
                blogId("blog-a"))).isZero();
        assertThat(jdbc.queryForObject("SELECT name FROM categories WHERE id = ?", String.class, categoryB))
                .isEqualTo("비분류");
    }
}
