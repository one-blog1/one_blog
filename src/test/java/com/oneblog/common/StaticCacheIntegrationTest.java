package com.oneblog.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import com.oneblog.IntegrationTestSupport;

/** 정적 파일 브라우저 캐시 (WebConfig, 4.2). Spring Security의 no-store가 덮어쓰지 않는지 확인한다. */
class StaticCacheIntegrationTest extends IntegrationTestSupport {

    @Test
    void 글꼴은_1년_동안_캐시한다() throws Exception {
        mvc.perform(get("/fonts/noto-serif-kr-2-700-normal.woff2"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", Matchers.containsString("max-age=31536000")))
                .andExpect(header().string("Cache-Control", Matchers.containsString("immutable")))
                .andExpect(header().string("Cache-Control", Matchers.not(Matchers.containsString("no-store"))));
    }

    @Test
    void CSS와_JS는_저장하되_매번_바뀌었는지_확인한다() throws Exception {
        for (String path : new String[] {"/css/app.css", "/js/lib/api.js"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-cache"));
        }
    }

    @Test
    void 없는_글꼴은_404() throws Exception {
        mvc.perform(get("/fonts/no-such-font.woff2")).andExpect(status().is4xxClientError());
    }
}
