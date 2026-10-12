package com.oneblog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/** 017 개인정보 처리방침·이용약관 화면은 비회원도 본다 (4.6). 하단 링크는 모든 화면이 불러오는 header.js가 붙인다. */
class PolicyPagesIntegrationTest extends IntegrationTestSupport {

    @Test
    void 처리방침과_약관은_로그인_없이_열린다() throws Exception {
        mvc.perform(get("/privacy.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("id=\"officer\"")));
        mvc.perform(get("/terms.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("id=\"sanctions\"")));
    }

    @Test
    void 공통_스크립트에_하단_링크가_있다() throws Exception {
        mvc.perform(get("/js/header.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("/privacy.html")))
                .andExpect(content().string(Matchers.containsString("/terms.html")));
    }
}
