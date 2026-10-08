package com.oneblog.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.oneblog.file.TestImages;

import jakarta.servlet.http.Cookie;

/** 블로그 만들기 (BLG-01, 6.4, 6.5, D-49, D-90 / quickstart 1, 2, 6~8, 21~25). */
class BlogCreateIntegrationTest extends BlogTestSupport {

    private Cookie owner;

    @BeforeEach
    void login() throws Exception {
        owner = signUpAndLogin("owner@example.com", "블로그장");
    }

    @Test
    void 블로그를_만들면_만든_회원이_블로그장이_된다() throws Exception {
        postBlog(owner, """
                {"name":"  제주 한 달 살기 ","slug":" Jeju-Month ","description":"제주 기록",
                 "visibility":"PUBLIC","joinPolicy":"APPROVAL"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("jeju-month"))
                .andExpect(jsonPath("$.url").value("/blog/jeju-month"))
                .andExpect(jsonPath("$.shareUrl").doesNotExist());

        Map<String, Object> blog = jdbc.queryForMap("SELECT * FROM blogs WHERE slug = 'jeju-month'");
        assertThat(blog.get("name")).isEqualTo("제주 한 달 살기");
        assertThat(blog.get("visibility")).isEqualTo("PUBLIC");
        assertThat(blog.get("join_policy")).isEqualTo("APPROVAL");
        assertThat(blog.get("status")).isEqualTo("ACTIVE");
        assertThat(((Number) blog.get("member_count")).intValue()).isEqualTo(1);
        assertThat(blog.get("share_token")).isNull();

        Map<String, Object> member = jdbc.queryForMap("""
                SELECT m.role, m.status, u.email FROM blog_members m JOIN users u ON u.id = m.user_id
                WHERE m.blog_id = ?""", blog.get("id"));
        assertThat(member.get("role")).isEqualTo("OWNER");
        assertThat(member.get("status")).isEqualTo("ACTIVE");
        assertThat(member.get("email")).isEqualTo("owner@example.com");
    }

    @Test
    void 주소_확인은_형식_예약어_중복을_알려준다() throws Exception {
        createBlog(owner, "taken-one", "PUBLIC").andExpect(status().isCreated());

        assertSlug("Admin", false, "RESERVED");
        assertSlug("ab", false, "INVALID_FORMAT");
        assertSlug("-abc", false, "INVALID_FORMAT");
        assertSlug("abc-", false, "INVALID_FORMAT");
        assertSlug("a--b", false, "INVALID_FORMAT");
        assertSlug("한글주소", false, "INVALID_FORMAT");
        assertSlug("a".repeat(31), false, "INVALID_FORMAT");
        assertSlug("TAKEN-ONE", false, "TAKEN");
        mvc.perform(get("/api/blog-slugs/availability").param("slug", " My-Blog1 ").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("my-blog1"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    private void assertSlug(String slug, boolean available, String reason) throws Exception {
        mvc.perform(get("/api/blog-slugs/availability").param("slug", slug).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(available))
                .andExpect(jsonPath("$.reason").value(reason));
    }

    @Test
    void 규칙에_맞지_않거나_이미_쓰인_주소로는_만들_수_없다() throws Exception {
        createBlog(owner, "same-slug", "PUBLIC").andExpect(status().isCreated());
        Cookie other = signUpAndLogin("other@example.com", "다른회원");

        createBlog(other, "same-slug", "PUBLIC")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLUG_TAKEN"));
        createBlog(other, "login", "PUBLIC")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESERVED_SLUG"));
        createBlog(other, "a_b", "PUBLIC")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SLUG"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blogs", Integer.class)).isEqualTo(1);
    }

    @Test
    void 필수값이_없거나_길이를_넘으면_이유를_알려준다() throws Exception {
        postBlog(owner, """
                {"name":"   ","slug":"no-name","description":"%s"}
                """.formatted("가".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'description')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'visibility')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'joinPolicy')]").exists());
        postBlog(owner, blogJson("가".repeat(51), "long-name", "PUBLIC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
        postBlog(owner, """
                {"name":"잘못된 값","slug":"bad-enum","visibility":"SECRET","joinPolicy":"OPEN"}
                """)
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blogs", Integer.class)).isZero();
    }

    @Test
    void 태그는_정리해서_중복_없이_저장한다() throws Exception {
        postBlog(owner, """
                {"name":"태그 블로그","slug":"tag-blog","visibility":"PUBLIC","joinPolicy":"OPEN",
                 "tags":["#맛집"," 서울  여행 ","Java","java","##맛집"]}
                """)
                .andExpect(status().isCreated());

        List<String> tags = jdbc.queryForList("""
                SELECT t.name FROM blog_tags bt JOIN tags t ON t.id = bt.tag_id
                JOIN blogs b ON b.id = bt.blog_id WHERE b.slug = 'tag-blog' ORDER BY t.name""", String.class);
        assertThat(tags).containsExactly("java", "맛집", "서울_여행");

        // 다른 블로그가 같은 태그를 쓰면 태그 행을 새로 만들지 않는다 (D-88)
        postBlog(owner, """
                {"name":"태그 블로그2","slug":"tag-blog2","visibility":"PUBLIC","joinPolicy":"OPEN","tags":["JAVA"]}
                """)
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tags WHERE name = 'java'", Integer.class)).isEqualTo(1);
    }

    @Test
    void 태그가_규칙에_맞지_않거나_10개를_넘으면_만들지_않는다() throws Exception {
        postBlog(owner, """
                {"name":"태그","slug":"bad-tag","visibility":"PUBLIC","joinPolicy":"OPEN",
                 "tags":["좋아요","맛집!","%s"]}
                """.formatted("가".repeat(21)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("tags[1]"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("tags[2]"));

        postBlog(owner, """
                {"name":"태그","slug":"many-tags","visibility":"PUBLIC","joinPolicy":"OPEN",
                 "tags":["t1","t2","t3","t4","t5","t6","t7","t8","t9","t10","t11"]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("tags"));

        // 같은 태그가 섞여 10개 이하로 합쳐지면 된다
        postBlog(owner, """
                {"name":"태그","slug":"dup-tags","visibility":"PUBLIC","joinPolicy":"OPEN",
                 "tags":["t1","t2","t3","t4","t5","t6","t7","t8","t9","t10","T1","#t2"]}
                """)
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blogs", Integer.class)).isEqualTo(1);
    }

    @Test
    void 일부_공개_블로그는_무작위_공유_링크를_만든다() throws Exception {
        String shareUrl = com.jayway.jsonpath.JsonPath.read(createBlog(owner, "link-blog", "UNLISTED")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.shareUrl");
        String token = jdbc.queryForObject("SELECT share_token FROM blogs WHERE slug = 'link-blog'", String.class);
        assertThat(token).matches("^[0-9a-f]{32}$");
        assertThat(shareUrl).isEqualTo("/blog/link-blog?key=" + token);

        createBlog(owner, "private-blog", "PRIVATE")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shareUrl").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT share_token FROM blogs WHERE slug = 'private-blog'", String.class))
                .isNull();
    }

    @Test
    void 로그인하지_않으면_만들기_업로드_내_블로그_모두_거부한다() throws Exception {
        mvc.perform(post("/api/blogs").with(org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blogJson("비회원", "guest-blog", "PUBLIC")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/blogs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/blog-quota")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/blog-slugs/availability").param("slug", "abc")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blogs", Integer.class)).isZero();
    }

    @Test
    void CSRF_토큰이_없으면_거부한다() throws Exception {
        mvc.perform(post("/api/blogs").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blogJson("CSRF", "no-csrf", "PUBLIC")))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blogs", Integer.class)).isZero();
    }

    @Test
    void 관리자_계정은_블로그를_만들_수_없다() throws Exception {
        makeAdmin("owner@example.com", "admin01");
        createBlog(owner, "admin-blog", "PUBLIC")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_NOT_ALLOWED"));
        mvc.perform(get("/api/me/blog-quota").cookie(owner))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blogs", Integer.class)).isZero();
    }

    @Test
    void 남이_올린_이미지나_없는_이미지로는_만들_수_없다() throws Exception {
        Cookie other = signUpAndLogin("other@example.com", "다른회원");
        Long othersFile = uploadPng(other);

        postBlog(owner, """
                {"name":"남의 이미지","slug":"others-cover","visibility":"PUBLIC","joinPolicy":"OPEN","coverFileId":%d}
                """.formatted(othersFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COVER_FILE"));
        postBlog(owner, """
                {"name":"없는 이미지","slug":"no-cover","visibility":"PUBLIC","joinPolicy":"OPEN","coverFileId":999999}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COVER_FILE"));

        Long myFile = uploadPng(owner);
        postBlog(owner, """
                {"name":"내 이미지","slug":"my-cover","visibility":"PUBLIC","joinPolicy":"OPEN","coverFileId":%d}
                """.formatted(myFile))
                .andExpect(status().isCreated());
        String url = jdbc.queryForObject("SELECT cover_image_url FROM blogs WHERE slug = 'my-cover'", String.class);
        String stored = jdbc.queryForObject("SELECT stored_name FROM files WHERE id = ?", String.class, myFile);
        assertThat(url).isEqualTo("/files/" + stored);
    }

    private Long uploadPng(Cookie login) throws Exception {
        String body = mvc.perform(MockMvcRequestBuilders
                        .multipart("/api/files/blog-cover")
                        .file(new MockMultipartFile("file", "cover.png", "image/png",
                                TestImages.png()))
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(login))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.fileId")).longValue();
    }

    @Test
    void 블로그_주소로_들어오면_블로그_화면을_준다() throws Exception {
        mvc.perform(get("/blog/anything"))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers
                        .forwardedUrl("/blog.html"));
    }
}
