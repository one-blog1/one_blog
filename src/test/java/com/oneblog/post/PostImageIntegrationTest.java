package com.oneblog.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;
import com.oneblog.file.TestImages;

import jakarta.servlet.http.Cookie;

/** 글 이미지 첨부 (BRD-05, 6.3 / specs/005-post-images). */
class PostImageIntegrationTest extends PostTestSupport {

    private Cookie member;

    @BeforeEach
    void setUp() throws Exception {
        Cookie owner = signUpAndLogin("owner@example.com", "블로그장");
        member = signUpAndLogin("member@example.com", "멤버회원");
        createBlog(owner, "photo-blog", "PUBLIC").andExpect(status().isCreated());
        addMember("photo-blog", "member@example.com", "MEMBER");
    }

    private String upload(Cookie who) throws Exception {
        String body = mvc.perform(multipart("/api/files/post-image")
                        .file(new MockMultipartFile("file", "a.png", "image/png", TestImages.png()))
                        .with(csrf()).cookie(who))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.url");
    }

    @Test
    void 본문에_넣은_내_이미지가_글에_연결되고_첫_이미지가_썸네일이다() throws Exception {
        String first = upload(member);
        String second = upload(member);
        Long id = writePost(member, "photo-blog", "사진 글", "![](" + second + ")\\n\\n![](" + first + ")");

        assertThat(jdbc.queryForList("SELECT CONCAT('/files/', stored_name) FROM files WHERE post_id = ? ORDER BY sort_order",
                String.class, id)).containsExactly(second, first);
        mvc.perform(get("/api/blogs/photo-blog/posts"))
                .andExpect(jsonPath("$.items[0].thumbnailUrl").value(second));
        mvc.perform(get("/api/posts/" + id))
                .andExpect(jsonPath("$.contentHtml").value(org.hamcrest.Matchers.containsString("src=\"" + second + "\"")));
    }

    @Test
    void 이미지는_글_하나에_10장까지() throws Exception {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            content.append("![](").append(upload(member)).append(") ");
        }
        writePost(member, "photo-blog", "{\"title\":\"많은 사진\",\"content\":\"" + content + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("content"));
    }

    @Test
    void 남이_올린_이미지는_내_글에_연결되지_않는다() throws Exception {
        Cookie other = signUpAndLogin("other@example.com", "다른회원");
        String others = upload(other);
        Long id = writePost(member, "photo-blog", "남의 사진", "![](" + others + ")");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM files WHERE post_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void 고칠_때_본문에서_뺀_이미지는_지운_것으로_표시한다() throws Exception {
        String keep = upload(member);
        String drop = upload(member);
        Long id = writePost(member, "photo-blog", "고칠 글", "![](" + keep + ") ![](" + drop + ")");
        mvc.perform(put("/api/posts/" + id).with(csrf()).cookie(member).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"고칠 글\",\"content\":\"![](" + keep + ")\"}"))
                .andExpect(status().isOk());
        String dropped = drop.substring("/files/".length());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM files WHERE stored_name = ?", Boolean.class,
                dropped)).isTrue();
        mvc.perform(get(drop)).andExpect(status().isNotFound());
        mvc.perform(get(keep)).andExpect(status().isOk());
    }

    @Test
    void 다른_사이트_이미지는_본문에_보이지_않는다() throws Exception {
        Long id = writePost(member, "photo-blog", "외부 사진", "![](https://tracker.example.com/a.png)");
        mvc.perform(get("/api/posts/" + id))
                .andExpect(jsonPath("$.contentHtml").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("tracker.example.com/a.png\""))));
    }

    @Test
    void 로그인하지_않으면_글_이미지를_올릴_수_없다() throws Exception {
        mvc.perform(multipart("/api/files/post-image")
                        .file(new MockMultipartFile("file", "a.png", "image/png", TestImages.png())).with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
