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
                "/js/components/blog-card.js", "/js/pages/home/blog-list.js", "/js/pages/blog-new.js", "/js/pages/my-blogs.js",
                "/js/pages/blog/blog.js",
                "/images/blog-default.svg", "/css/app.css", "/favicon.ico", "/images/favicon.svg",
                "/images/apple-touch-icon.png"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @Test
    void 없는_화면은_막힌다() throws Exception {
        // 정한 주소 밖은 보안 설정(anyRequest().denyAll())이 파일을 찾기 전에 막는다. 404가 아니라 401이어야
        // denyAll이 빠졌을 때 이 테스트가 실패한다 (029 US1, SEC-11)
        mvc.perform(get("/no-such-page.html")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/login.html")).andExpect(status().isOk());
    }

    @Test
    void 글쓰기_글_수정_블로그_관리_화면을_받는다() throws Exception {
        // 화면 HTML은 비회원도 받는다. 볼 수 있는지·고칠 수 있는지는 API가 지킨다 (research R10, 029 US6)
        for (String path : new String[] {"/blog/any-blog/write", "/blog/any-blog/posts/1/edit", "/blog/any-blog/manage"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                            .contentTypeCompatibleWith(org.springframework.http.MediaType.TEXT_HTML));
        }
    }
}
