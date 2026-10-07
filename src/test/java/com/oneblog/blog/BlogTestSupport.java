package com.oneblog.blog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.oneblog.IntegrationTestSupport;

import jakarta.servlet.http.Cookie;

/** 블로그 통합 테스트 공통 도우미. */
abstract class BlogTestSupport extends IntegrationTestSupport {

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
}
