package com.oneblog.post;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.oneblog.blog.BlogTestSupport;

import jakarta.servlet.http.Cookie;

/** 글 테스트 공통 도우미. */
public abstract class PostTestSupport extends BlogTestSupport {

    protected ResultActions writePost(Cookie login, String slug, String json) throws Exception {
        return mvc.perform(post("/api/blogs/" + slug + "/posts").with(csrf()).cookie(login)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected Long writePost(Cookie login, String slug, String title, String content) throws Exception {
        String body = writePost(login, slug, """
                {"title":"%s","content":"%s"}
                """.formatted(title, content))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
