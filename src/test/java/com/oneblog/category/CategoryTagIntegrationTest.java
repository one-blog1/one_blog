package com.oneblog.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;
import com.oneblog.post.PostTestSupport;

import jakarta.servlet.http.Cookie;

/** 카테고리·태그 (BRD-03, BRD-04, 6.4 / specs/008-categories-tags). */
class CategoryTagIntegrationTest extends PostTestSupport {

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

    private Long createCategory(Cookie login, String slug, String name) throws Exception {
        String body = mvc.perform(post("/api/blogs/" + slug + "/categories").with(csrf()).cookie(login)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private Long writeWith(Cookie login, String slug, String extraJson) throws Exception {
        String body = writePost(login, slug, "{\"title\":\"글\",\"content\":\"본문\"," + extraJson + "}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void 블로그장은_카테고리를_만들고_바꾸고_지운다() throws Exception {
        Long daily = createCategory(owner, "open-blog", "일상");
        Long dev = createCategory(owner, "open-blog", "개발");

        mvc.perform(get("/api/blogs/open-blog/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("일상", "개발")));

        mvc.perform(put("/api/categories/" + dev).with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"코딩\",\"sortOrder\":0}"))
                .andExpect(status().isNoContent());
        mvc.perform(put("/api/categories/" + daily).with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sortOrder\":1}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/blogs/open-blog/categories"))
                .andExpect(jsonPath("$[*].name", contains("코딩", "일상")));

        // 같은 이름, 빈 이름
        mvc.perform(post("/api/blogs/open-blog/categories").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"일상\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_NAME_TAKEN"));
        mvc.perform(post("/api/blogs/open-blog/categories").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/categories/" + daily).with(csrf()).cookie(owner))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/blogs/open-blog/categories"))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void 일반_멤버와_다른_회원은_카테고리를_관리할_수_없다() throws Exception {
        Long id = createCategory(owner, "open-blog", "일상");
        for (Cookie login : new Cookie[] {member, stranger}) {
            mvc.perform(post("/api/blogs/open-blog/categories").with(csrf()).cookie(login)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"몰래\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/categories/" + id).with(csrf()).cookie(login)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"몰래\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/categories/" + id).with(csrf()).cookie(login))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/blogs/open-blog/categories").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"몰래\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 블로그_정보에_내_관리_권한이_온다() throws Exception {
        mvc.perform(get("/api/blogs/open-blog").cookie(owner))
                .andExpect(jsonPath("$.permissions.canManagePosts").value(true));
        mvc.perform(get("/api/blogs/open-blog").cookie(member))
                .andExpect(jsonPath("$.permissions.canManagePosts").value(false));
        mvc.perform(get("/api/blogs/open-blog").cookie(stranger))
                .andExpect(jsonPath("$.permissions").doesNotExist());
    }

    @Test
    void 글에_카테고리를_달고_카테고리별로_본다() throws Exception {
        Long daily = createCategory(owner, "open-blog", "일상");
        Long inDaily = writeWith(member, "open-blog", "\"categoryId\":" + daily);
        writeWith(member, "open-blog", "\"categoryId\":null");

        mvc.perform(get("/api/posts/" + inDaily))
                .andExpect(jsonPath("$.categoryId").value(daily))
                .andExpect(jsonPath("$.categoryName").value("일상"));
        mvc.perform(get("/api/blogs/open-blog/posts").param("category", String.valueOf(daily)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].categoryName").value("일상"));
        mvc.perform(get("/api/blogs/open-blog/categories"))
                .andExpect(jsonPath("$[0].postCount").value(1));

        // 다른 블로그의 카테고리는 달 수 없다
        createBlog(stranger, "other-blog", "PUBLIC").andExpect(status().isCreated());
        Long foreign = createCategory(stranger, "other-blog", "남의것");
        writePost(member, "open-blog", "{\"title\":\"글\",\"content\":\"본문\",\"categoryId\":" + foreign + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("categoryId"));

        // 카테고리를 지우면 글은 분류 없음
        mvc.perform(delete("/api/categories/" + daily).with(csrf()).cookie(owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + inDaily))
                .andExpect(jsonPath("$.categoryId").doesNotExist())
                .andExpect(jsonPath("$.categoryName").doesNotExist());
    }

    @Test
    void 태그를_정리해_달고_고치면_바뀐다() throws Exception {
        Long id = writeWith(member, "open-blog", "\"tags\":[\"#Java\",\" 맛집 탐방 \",\"java\"]");
        mvc.perform(get("/api/posts/" + id))
                .andExpect(jsonPath("$.tags", contains("java", "맛집_탐방")));
        mvc.perform(get("/api/blogs/open-blog/posts"))
                .andExpect(jsonPath("$.items[0].tags", contains("java", "맛집_탐방")));

        mvc.perform(put("/api/posts/" + id).with(csrf()).cookie(member).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"글\",\"content\":\"본문\",\"tags\":[\"spring\"]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + id)).andExpect(jsonPath("$.tags", contains("spring")));
        Integer links = jdbc.queryForObject("SELECT COUNT(*) FROM post_tags WHERE post_id = ?", Integer.class, id);
        assertThat(links).isEqualTo(1);
    }

    @Test
    void 태그_규칙을_어기면_거부한다() throws Exception {
        writePost(member, "open-blog", "{\"title\":\"글\",\"content\":\"본문\",\"tags\":[\"좋아요!\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("tags[0]"));
        writePost(member, "open-blog", "{\"title\":\"글\",\"content\":\"본문\",\"tags\":"
                        + "[\"a1\",\"a2\",\"a3\",\"a4\",\"a5\",\"a6\",\"a7\",\"a8\",\"a9\",\"a10\",\"a11\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("tags"));
        Integer posts = jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class);
        assertThat(posts).isZero();
    }

    @Test
    void 태그_목록은_전체_공개_블로그의_보이는_글만_보여준다() throws Exception {
        Long shown = writeWith(member, "open-blog", "\"tags\":[\"여행\"]");
        Long hidden = writeWith(member, "open-blog", "\"tags\":[\"여행\"]");
        jdbc.update("UPDATE posts SET is_hidden = 1 WHERE id = ?", hidden);

        createBlog(owner, "secret-blog", "PRIVATE").andExpect(status().isCreated());
        writeWith(owner, "secret-blog", "\"tags\":[\"여행\"]");
        createBlog(stranger, "link-blog", "UNLISTED").andExpect(status().isCreated());
        writeWith(stranger, "link-blog", "\"tags\":[\"여행\"]");

        mvc.perform(get("/api/tags/여행/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(shown))
                .andExpect(jsonPath("$.items[0].blogSlug").value("open-blog"))
                .andExpect(jsonPath("$.items[0].tags", contains("여행")));
        // #과 대문자를 정리해서 찾는다, 없는 태그는 빈 목록
        mvc.perform(get("/api/tags/{name}/posts", "#여행")).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/tags/없는태그/posts")).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/tags/여행")).andExpect(forwardedUrl("/tag.html"));
    }
}
