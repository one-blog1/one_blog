package com.oneblog.view;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.oneblog.post.PostTestSupport;

import jakarta.servlet.http.Cookie;

/** 조회수와 SNS 미리보기 (BRD-07, BRD-11, 6.2, 6.7 / specs/014-share-views). */
class ShareViewIntegrationTest extends PostTestSupport {

    private static final String BROWSER = "Mozilla/5.0 (Macintosh) Chrome/130";

    private Cookie owner;
    private Long postId;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUpAndLogin("owner@example.com", "블로그장");
        createBlog(owner, "open-blog", "PUBLIC").andExpect(status().isCreated());
        postId = writePost(owner, "open-blog", "<제목> & \\\"따옴표\\\"", "본문");
    }

    private int viewCount() {
        return jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Integer.class, postId);
    }

    @Test
    void 같은_사람이_같은_날_보면_한_번만_센다() throws Exception {
        mvc.perform(get("/api/posts/" + postId).header("User-Agent", BROWSER))
                .andExpect(jsonPath("$.viewCount").value(1));
        mvc.perform(get("/api/posts/" + postId).header("User-Agent", BROWSER))
                .andExpect(jsonPath("$.viewCount").value(1));
        // 다른 브라우저(다른 사람)는 따로 센다
        mvc.perform(get("/api/posts/" + postId).header("User-Agent", BROWSER + " Firefox"));
        org.assertj.core.api.Assertions.assertThat(viewCount()).isEqualTo(2);
        // 회원은 회원 ID로 센다
        mvc.perform(get("/api/posts/" + postId).cookie(owner).header("User-Agent", BROWSER));
        mvc.perform(get("/api/posts/" + postId).cookie(owner).header("User-Agent", "Other"));
        org.assertj.core.api.Assertions.assertThat(viewCount()).isEqualTo(3);
        // 봇과 관리자는 세지 않는다
        mvc.perform(get("/api/posts/" + postId).header("User-Agent", "Twitterbot/1.0"));
        mvc.perform(get("/api/posts/" + postId).cookie(createAdminAndLogin("admin01")).header("User-Agent", BROWSER));
        org.assertj.core.api.Assertions.assertThat(viewCount()).isEqualTo(3);
    }

    @Test
    void 공개_글은_미리보기_태그를_채우고_특수문자를_바꾼다() throws Exception {
        mvc.perform(get("/blog/open-blog/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("property=\"og:title\" content=\"&lt;제목&gt; &amp; &quot;따옴표&quot;\"")))
                .andExpect(content().string(containsString("블로그장 · 블로그 open-blog")))
                .andExpect(content().string(containsString("og:url")))
                .andExpect(content().string(not(containsString("<제목>"))));
    }

    @Test
    void 볼_수_없는_글은_미리보기에_내용을_넣지_않는다() throws Exception {
        createBlog(owner, "secret-blog", "PRIVATE").andExpect(status().isCreated());
        Long secret = writePost(owner, "secret-blog", "비밀 제목", "본문");
        mvc.perform(get("/blog/secret-blog/posts/" + secret))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("비밀 제목"))))
                .andExpect(content().string(containsString("id=\"post\"")));
        mvc.perform(get("/blog/open-blog/posts/abc")).andExpect(status().isOk());
    }
}
