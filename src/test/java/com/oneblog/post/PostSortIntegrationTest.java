package com.oneblog.post;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** 블로그 글 목록 최신순·인기순 (BRD-02, D-89, D-129). 고른 카테고리 안에서도 같은 정렬. */
class PostSortIntegrationTest extends PostTestSupport {

    private Long old;
    private Long mid;
    private Long recent;
    private Long baseball;

    @BeforeEach
    void setUp() throws Exception {
        Cookie owner = signUpAndLogin("sort@example.com", "정렬회원");
        createBlog(owner, "sort-blog", "PUBLIC").andExpect(status().isCreated());
        String body = mvc.perform(post("/api/blogs/sort-blog/categories").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"야구\"}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        baseball = ((Number) JsonPath.read(body, "$.id")).longValue();

        old = writePost(owner, "sort-blog", "오래된 글", "내용");
        mid = writePost(owner, "sort-blog", "중간 글", "내용");
        recent = writePost(owner, "sort-blog", "최근 글", "내용");
        // 좋아요 수: 중간 5, 오래된 5, 최근 1. 같으면 최신순이라 중간 → 오래된
        jdbc.update("UPDATE posts SET like_count = 5 WHERE id IN (?, ?)", old, mid);
        jdbc.update("UPDATE posts SET like_count = 1 WHERE id = ?", recent);
        jdbc.update("UPDATE posts SET created_at = created_at - INTERVAL 2 DAY WHERE id = ?", old);
        jdbc.update("UPDATE posts SET created_at = created_at - INTERVAL 1 DAY WHERE id = ?", mid);
        jdbc.update("UPDATE posts SET category_id = ? WHERE id IN (?, ?)", baseball, old, recent);
    }

    @Test
    void 기본은_최신순() throws Exception {
        mvc.perform(get("/api/blogs/sort-blog/posts"))
                .andExpect(jsonPath("$.sort").value("latest"))
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(
                        recent.intValue(), mid.intValue(), old.intValue())));
    }

    @Test
    void 인기순은_좋아요_많은_순이고_같으면_최신순() throws Exception {
        mvc.perform(get("/api/blogs/sort-blog/posts?sort=popular"))
                .andExpect(jsonPath("$.sort").value("popular"))
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(
                        mid.intValue(), old.intValue(), recent.intValue())));
    }

    @Test
    void 카테고리_안에서도_최신순_인기순() throws Exception {
        mvc.perform(get("/api/blogs/sort-blog/posts?category=" + baseball))
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(recent.intValue(), old.intValue())));
        mvc.perform(get("/api/blogs/sort-blog/posts?category=" + baseball + "&sort=popular"))
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(old.intValue(), recent.intValue())));
    }

    @Test
    void 모르는_정렬_값은_최신순() throws Exception {
        mvc.perform(get("/api/blogs/sort-blog/posts?sort=views;drop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sort").value("latest"));
    }
}
