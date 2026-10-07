package com.oneblog.blog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.oneblog.IntegrationTestSupport;

/** 002 화면과 정적 파일은 비회원도 받는다. 데이터는 API가 지킨다 (research R10). */
class BlogPagesIntegrationTest extends IntegrationTestSupport {

    @Test
    void 화면과_스크립트를_받을_수_있다() throws Exception {
        for (String path : new String[] {"/", "/index.html", "/blog-new.html", "/my-blogs.html", "/blog.html",
                "/js/blog-card.js", "/js/blog-list.js", "/js/blog-new.js", "/js/my-blogs.js", "/js/blog.js",
                "/images/blog-default.svg", "/css/app.css"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @Test
    void 없는_화면은_막힌다() throws Exception {
        mvc.perform(get("/no-such-page.html")).andExpect(status().is4xxClientError());
    }
}
