package com.oneblog.blog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.Cookie;

/** 메인 블로그 목록과 내 블로그 (BLG-02, BLG-06, D-06, D-76, D-89 / quickstart 13~16). */
class BlogListIntegrationTest extends BlogTestSupport {

    private Cookie alice;
    private Cookie bob;

    @BeforeEach
    void setUp() throws Exception {
        alice = signUpAndLogin("alice@example.com", "앨리스");
        bob = signUpAndLogin("bob@example.com", "밥아저씨");
    }

    @Test
    void 목록에는_공개_블로그만_최신순으로_나온다() throws Exception {
        postBlog(alice, """
                {"name":"첫 공개","slug":"a-first","visibility":"PUBLIC","joinPolicy":"OPEN","tags":["여행"]}
                """);
        createBlog(alice, "a-link", "UNLISTED");
        createBlog(alice, "a-private", "PRIVATE");
        createBlog(bob, "b-public", "PUBLIC");
        jdbc.update("UPDATE blogs SET is_hidden = 1 WHERE slug = 'b-public'");
        createBlog(bob, "b-second", "PUBLIC");

        mvc.perform(get("/api/blogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sort").value("latest"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("b-second"))
                .andExpect(jsonPath("$.items[0].ownerNickname").value("밥아저씨"))
                .andExpect(jsonPath("$.items[1].slug").value("a-first"))
                .andExpect(jsonPath("$.items[1].tags[0]").value("여행"))
                .andExpect(jsonPath("$.items[1].memberCount").value(1))
                .andExpect(jsonPath("$.items[1].coverImageUrl").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.items[*].slug", Matchers.not(Matchers.hasItem("a-link"))))
                .andExpect(jsonPath("$.items[*].slug", Matchers.not(Matchers.hasItem("a-private"))));
    }

    @Test
    void 인기순은_멤버_수가_많은_순서() throws Exception {
        createBlog(alice, "small", "PUBLIC");
        createBlog(alice, "big", "PUBLIC");
        createBlog(bob, "newest", "PUBLIC");
        jdbc.update("UPDATE blogs SET member_count = 5 WHERE slug = 'small'");
        jdbc.update("UPDATE blogs SET member_count = 9 WHERE slug = 'big'");

        mvc.perform(get("/api/blogs").param("sort", "popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sort").value("popular"))
                .andExpect(jsonPath("$.items[0].slug").value("big"))
                .andExpect(jsonPath("$.items[1].slug").value("small"))
                .andExpect(jsonPath("$.items[2].slug").value("newest"));
    }

    @Test
    void 페이지와_개수가_규칙_밖이면_기본값으로() throws Exception {
        for (int i = 1; i <= 3; i++) {
            createBlog(alice, "page-" + i, "PUBLIC");
            createBlog(bob, "bpage-" + i, "PUBLIC");
        }
        mvc.perform(get("/api/blogs").param("page", "999").param("size", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.items.length()").value(6));
        mvc.perform(get("/api/blogs").param("page", "abc").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void 페이지를_넘긴다() throws Exception {
        // 회원마다 공개 3개 제한이 있어 DB에 직접 넣는다
        Long aliceId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'alice@example.com'", Long.class);
        for (int i = 1; i <= 25; i++) {
            jdbc.update("INSERT INTO blogs (slug, name, visibility, join_policy) VALUES (?, ?, 'PUBLIC', 'OPEN')",
                    "bulk-" + i, "블로그 " + i);
            Long blogId = jdbc.queryForObject("SELECT id FROM blogs WHERE slug = ?", Long.class, "bulk-" + i);
            jdbc.update("INSERT INTO blog_members (user_id, blog_id, role) VALUES (?, ?, 'OWNER')", aliceId, blogId);
        }
        mvc.perform(get("/api/blogs").param("page", "3").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(3))
                .andExpect(jsonPath("$.totalItems").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items.length()").value(5));
        mvc.perform(get("/api/blogs").param("size", "20"))
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void 블로그가_없으면_빈_목록() throws Exception {
        mvc.perform(get("/api/blogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void 내_블로그는_공개_범위와_상관없이_보이고_역할로_나뉜다() throws Exception {
        createBlog(alice, "my-public", "PUBLIC");
        createBlog(alice, "my-link", "UNLISTED");
        createBlog(alice, "my-private", "PRIVATE");
        createBlog(bob, "bob-blog", "PUBLIC");
        // 참여 기능(003) 전이라 멤버십을 직접 넣는다
        Long aliceId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'alice@example.com'", Long.class);
        Long bobBlog = jdbc.queryForObject("SELECT id FROM blogs WHERE slug = 'bob-blog'", Long.class);
        jdbc.update("INSERT INTO blog_members (user_id, blog_id, role) VALUES (?, ?, 'MEMBER')", aliceId, bobBlog);

        mvc.perform(get("/api/me/blogs").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owned.length()").value(3))
                .andExpect(jsonPath("$.owned[*].slug",
                        Matchers.containsInAnyOrder("my-public", "my-link", "my-private")))
                .andExpect(jsonPath("$.owned[?(@.slug == 'my-private')].visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.owned[0].role").value("OWNER"))
                .andExpect(jsonPath("$.joined.length()").value(1))
                .andExpect(jsonPath("$.joined[0].slug").value("bob-blog"))
                .andExpect(jsonPath("$.joined[0].role").value("MEMBER"));

        mvc.perform(get("/api/me/blogs").cookie(bob))
                .andExpect(jsonPath("$.owned.length()").value(1))
                .andExpect(jsonPath("$.joined.length()").value(0));
    }

    @Test
    void 블로그가_없는_회원의_내_블로그는_비어_있다() throws Exception {
        mvc.perform(get("/api/me/blogs").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owned.length()").value(0))
                .andExpect(jsonPath("$.joined.length()").value(0));
    }
}
