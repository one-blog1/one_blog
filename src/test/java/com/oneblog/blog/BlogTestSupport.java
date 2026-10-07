package com.oneblog.blog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.oneblog.IntegrationTestSupport;

import jakarta.servlet.http.Cookie;

/** 블로그 통합 테스트 공통 도우미. 다른 기능(참여·글·댓글 등)의 테스트도 이어받아 쓴다. */
public abstract class BlogTestSupport extends IntegrationTestSupport {

    /** 블로그 만들기 요청을 보낸다. 결과 검사는 호출하는 쪽에서 한다. */
    protected ResultActions postBlog(Cookie login, String json) throws Exception {
        return mvc.perform(post("/api/blogs").with(csrf()).cookie(login)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    protected String blogJson(String name, String slug, String visibility) {
        return """
                {"name":"%s","slug":"%s","visibility":"%s","joinPolicy":"OPEN"}
                """.formatted(name, slug, visibility);
    }

    protected ResultActions createBlog(Cookie login, String slug, String visibility) throws Exception {
        return postBlog(login, blogJson("블로그 " + slug, slug, visibility));
    }

    /** 참여 방식까지 정해 블로그를 만든다. */
    protected ResultActions createBlog(Cookie login, String slug, String visibility, String joinPolicy)
            throws Exception {
        return postBlog(login, """
                {"name":"블로그 %s","slug":"%s","visibility":"%s","joinPolicy":"%s"}
                """.formatted(slug, slug, visibility, joinPolicy));
    }

    protected Long blogId(String slug) {
        return jdbc.queryForObject("SELECT id FROM blogs WHERE slug = ?", Long.class, slug);
    }

    protected Long userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email.toLowerCase());
    }

    /** 참여 기능을 거치지 않고 멤버십을 바로 넣는다 (다른 기능의 테스트 준비용). */
    protected void addMember(String slug, String email, String role) {
        jdbc.update("INSERT INTO blog_members (user_id, blog_id, role) VALUES (?, ?, ?)", userId(email), blogId(slug),
                role);
        jdbc.update("UPDATE blogs SET member_count = member_count + 1 WHERE slug = ?", slug);
    }
}
