package com.oneblog.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.Cookie;

/** 블로그 생성 개수 제한 (BLG-10, D-51, D-52, D-68 / quickstart 9~11). */
class BlogLimitIntegrationTest extends BlogTestSupport {

    private Cookie owner;

    @BeforeEach
    void login() throws Exception {
        owner = signUpAndLogin("limit@example.com", "제한회원");
    }

    @Test
    void 일부_공개는_공개_개수에_들어가_3개를_넘으면_거부한다() throws Exception {
        createBlog(owner, "pub-1", "PUBLIC").andExpect(status().isCreated());
        createBlog(owner, "pub-2", "PUBLIC").andExpect(status().isCreated());
        createBlog(owner, "link-1", "UNLISTED").andExpect(status().isCreated());

        createBlog(owner, "pub-4", "PUBLIC")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BLOG_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.limitType").value("PUBLIC"))
                .andExpect(jsonPath("$.limit").value(3));
        createBlog(owner, "link-2", "UNLISTED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.limitType").value("PUBLIC"));

        // 공개와 비공개는 따로 센다
        createBlog(owner, "priv-1", "PRIVATE").andExpect(status().isCreated());

        mvc.perform(get("/api/me/blog-quota").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.public.used").value(3))
                .andExpect(jsonPath("$.public.limit").value(3))
                .andExpect(jsonPath("$.public.remaining").value(0))
                .andExpect(jsonPath("$.private.used").value(1))
                .andExpect(jsonPath("$.private.remaining").value(4));
    }

    @Test
    void 비공개는_5개까지() throws Exception {
        for (int i = 1; i <= 5; i++) {
            createBlog(owner, "priv-" + i, "PRIVATE").andExpect(status().isCreated());
        }
        createBlog(owner, "priv-6", "PRIVATE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.limitType").value("PRIVATE"))
                .andExpect(jsonPath("$.limit").value(5));
    }

    @Test
    void 다른_회원의_블로그는_내_개수에_들어가지_않는다() throws Exception {
        Cookie other = signUpAndLogin("other@example.com", "다른회원");
        createBlog(other, "other-1", "PUBLIC").andExpect(status().isCreated());
        createBlog(other, "other-2", "PUBLIC").andExpect(status().isCreated());
        createBlog(other, "other-3", "PUBLIC").andExpect(status().isCreated());
        createBlog(owner, "mine-1", "PUBLIC").andExpect(status().isCreated());
    }

    @Test
    void 동시에_여러_번_만들어도_제한을_넘지_않는다() throws Exception {
        createBlog(owner, "pre-1", "PUBLIC").andExpect(status().isCreated());
        createBlog(owner, "pre-2", "PUBLIC").andExpect(status().isCreated());

        int requests = 10;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++) {
                String slug = "race-" + i;
                results.add(pool.submit(() -> {
                    start.await();
                    return createBlog(owner, slug, "PUBLIC").andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            int created = 0;
            int rejected = 0;
            for (Future<Integer> result : results) {
                int status = result.get(30, TimeUnit.SECONDS);
                if (status == 201) {
                    created++;
                } else if (status == 409) {
                    rejected++;
                }
            }
            assertThat(created).isEqualTo(1);
            assertThat(rejected).isEqualTo(requests - 1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM blogs b JOIN blog_members m ON m.blog_id = b.id
                JOIN users u ON u.id = m.user_id
                WHERE u.email = 'limit@example.com' AND b.visibility IN ('PUBLIC', 'UNLISTED')""", Integer.class))
                .isEqualTo(3);
    }
}
