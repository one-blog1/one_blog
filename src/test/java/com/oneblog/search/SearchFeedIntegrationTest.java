package com.oneblog.search;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.oneblog.post.PostTestSupport;

import jakarta.servlet.http.Cookie;

/** 통합 검색·메인 피드·최근 검색어 (BRD-08, BRD-09, BLG-03, 6.1 / specs/010-search-feed). */
class SearchFeedIntegrationTest extends PostTestSupport {

    private Cookie alice;
    private Cookie bob;
    private Long travelPost;
    private Long secretPost;

    @BeforeEach
    void setUp() throws Exception {
        alice = signUpAndLogin("alice@example.com", "앨리스");
        bob = signUpAndLogin("bob@example.com", "밥아저씨");
        postBlog(alice, """
                {"name":"제주 여행 일기","slug":"jeju-trip","visibility":"PUBLIC","joinPolicy":"OPEN","tags":["여행"]}
                """).andExpect(status().isCreated());
        createBlog(alice, "secret-trip", "PRIVATE").andExpect(status().isCreated());
        travelPost = idOf(writePost(alice, "jeju-trip",
                "{\"title\":\"제주도 여행 첫날\",\"content\":\"본문\",\"tags\":[\"맛집\"]}"));
        secretPost = idOf(writePost(alice, "secret-trip",
                "{\"title\":\"제주도 여행 비밀\",\"content\":\"본문\",\"tags\":[\"맛집\"]}"));
    }

    private Long idOf(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        String body = actions.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void 글은_제목_태그_작성자로_찾고_비공개_블로그_글은_빠진다() throws Exception {
        mvc.perform(get("/api/search/posts").param("q", "제주도"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains(travelPost.intValue())))
                .andExpect(jsonPath("$.items[0].blogSlug").value("jeju-trip"));
        mvc.perform(get("/api/search/posts").param("q", "#맛집").param("target", "tag"))
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/search/posts").param("q", "앨리스").param("target", "author"))
                .andExpect(jsonPath("$.items[*].id", contains(travelPost.intValue())));
        // 범위를 제목으로 고르면 닉네임으로는 찾지 않는다
        mvc.perform(get("/api/search/posts").param("q", "앨리스").param("target", "title"))
                .andExpect(jsonPath("$.items", hasSize(0)));

        jdbc.update("UPDATE posts SET is_hidden = 1 WHERE id = ?", travelPost);
        mvc.perform(get("/api/search/posts").param("q", "제주도"))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void 블로그는_이름_소개와_블로그_태그로_찾는다() throws Exception {
        mvc.perform(get("/api/search/blogs").param("q", "여행 일기"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].slug", contains("jeju-trip")));
        mvc.perform(get("/api/search/blogs").param("q", "여행").param("sort", "popular"))
                .andExpect(jsonPath("$.items[*].slug", contains("jeju-trip")))
                .andExpect(jsonPath("$.sort").value("popular"));
    }

    @Test
    void 빈칸_특수문자만_한_글자_검색어는_막는다() throws Exception {
        for (String q : new String[] {"  ", "!!@@", "a", "가나다라마바사아자차카타파하가나다라마바사"}) {
            mvc.perform(get("/api/search/posts").param("q", q))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("q"));
        }
        // 검색식 기호는 지워서 그대로 찾는다
        mvc.perform(get("/api/search/posts").param("q", "+제주도* -\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void 최근_검색어는_본인만_최대_10개() throws Exception {
        for (int i = 0; i < 12; i++) {
            mvc.perform(get("/api/search/posts").param("q", "검색어" + i).cookie(bob)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/search/posts").param("q", "검색어5").cookie(bob));
        String body = mvc.perform(get("/api/me/recent-searches").cookie(bob))
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].keyword").value("검색어5"))
                .andReturn().getResponse().getContentAsString();
        Integer firstId = JsonPath.read(body, "$[0].id");

        // 남의 검색어는 지울 수 없다
        mvc.perform(delete("/api/me/recent-searches/" + firstId).with(csrf()).cookie(alice))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me/recent-searches").cookie(bob)).andExpect(jsonPath("$", hasSize(10)));
        mvc.perform(delete("/api/me/recent-searches/" + firstId).with(csrf()).cookie(bob));
        mvc.perform(get("/api/me/recent-searches").cookie(bob)).andExpect(jsonPath("$", hasSize(9)));
        mvc.perform(delete("/api/me/recent-searches").with(csrf()).cookie(bob));
        mvc.perform(get("/api/me/recent-searches").cookie(bob)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/me/recent-searches")).andExpect(status().isUnauthorized());
    }

    @Test
    void 피드는_최신_인기와_팔로우_구독_탭을_나눈다() throws Exception {
        createBlog(bob, "bob-blog", "PUBLIC").andExpect(status().isCreated());
        Long bobPost = writePost(bob, "bob-blog", "밥의 글", "본문");
        jdbc.update("UPDATE posts SET like_count = 5 WHERE id = ?", travelPost);

        mvc.perform(get("/api/feed"))
                .andExpect(jsonPath("$.items[*].id", contains(bobPost.intValue(), travelPost.intValue())));
        mvc.perform(get("/api/feed").param("tab", "popular"))
                .andExpect(jsonPath("$.items[0].id").value(travelPost));
        mvc.perform(get("/api/feed").param("tab", "following")).andExpect(status().isUnauthorized());

        // 팔로우 탭: 앨리스를 팔로우하면 앨리스의 글 중 내가 볼 수 있는 것만 (비공개 블로그 글은 빠짐)
        mvc.perform(post("/api/users/앨리스/follow").with(csrf()).cookie(bob));
        mvc.perform(get("/api/feed").param("tab", "following").cookie(bob))
                .andExpect(jsonPath("$.items[*].id", contains(travelPost.intValue())));
        // 비공개 블로그의 멤버가 되면 그 글도 보인다
        addMember("secret-trip", "bob@example.com", "MEMBER");
        mvc.perform(get("/api/feed").param("tab", "following").cookie(bob))
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(travelPost.intValue(), secretPost.intValue())));

        // 구독 탭
        mvc.perform(get("/api/feed").param("tab", "subscriptions").cookie(bob)).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(post("/api/blogs/jeju-trip/subscription").with(csrf()).cookie(bob));
        mvc.perform(get("/api/feed").param("tab", "subscriptions").cookie(bob))
                .andExpect(jsonPath("$.items[*].id", contains(travelPost.intValue())));
    }
}
