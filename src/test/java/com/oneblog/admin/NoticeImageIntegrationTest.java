package com.oneblog.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;
import com.oneblog.IntegrationTestSupport;
import com.oneblog.file.TestImages;

import jakarta.servlet.http.Cookie;

/** 020 메인 공지 사진: 관리자가 올린 사진을 공지에 연결해 30일 정리 배치에서 빼고, 빠지거나 공지를 지우면 지운 것으로 표시한다. */
class NoticeImageIntegrationTest extends IntegrationTestSupport {

    private Cookie admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = createAdminAndLogin("admin01");
    }

    private String uploadImage() throws Exception {
        String body = mvc.perform(multipart("/api/files/post-image")
                        .file(new MockMultipartFile("file", "a.png", "image/png", TestImages.png()))
                        .with(csrf()).cookie(admin))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.url");
    }

    private Long fileIdOf(String url) {
        String storedName = url.substring("/files/".length());
        return jdbc.queryForObject("SELECT id FROM files WHERE stored_name = ?", Long.class, storedName);
    }

    private String json(String title, String content) {
        return "{\"title\":\"" + title + "\",\"content\":\"" + content + "\",\"category\":\"GENERAL\"}";
    }

    @Test
    void 공지에_넣은_사진은_공지에_연결되고_화면에_보인다() throws Exception {
        String url = uploadImage();
        String body = mvc.perform(post("/api/admin/notices").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json("사진 공지", "![](" + url + ")")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number noticeId = JsonPath.read(body, "$.id");

        Long postId = jdbc.queryForObject("SELECT post_id FROM files WHERE id = ?", Long.class, fileIdOf(url));
        assertThat(postId).isEqualTo(noticeId.longValue());
        mvc.perform(get("/api/notices/" + noticeId))
                .andExpect(jsonPath("$.contentHtml").value(Matchers.containsString(url)));
    }

    @Test
    void 고칠_때_빠진_사진과_지운_공지의_사진은_지운_것으로_표시한다() throws Exception {
        String first = uploadImage();
        String second = uploadImage();
        String body = mvc.perform(post("/api/admin/notices").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("사진 두 장", "![](" + first + ") ![](" + second + ")")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number noticeId = JsonPath.read(body, "$.id");

        mvc.perform(put("/api/admin/notices/" + noticeId).with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json("사진 한 장", "![](" + second + ")")))
                .andExpect(status().is2xxSuccessful());
        assertThat(deletedAt(first)).isNotNull();
        assertThat(deletedAt(second)).isNull();

        mvc.perform(delete("/api/admin/notices/" + noticeId).with(csrf()).cookie(admin))
                .andExpect(status().isNoContent());
        assertThat(deletedAt(second)).isNotNull();
    }

    @Test
    void 사진이_10장을_넘으면_400() throws Exception {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            content.append("![](").append(uploadImage()).append(") ");
        }
        mvc.perform(post("/api/admin/notices").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json("많은 사진", content.toString())))
                .andExpect(status().isBadRequest());
    }

    private Object deletedAt(String url) {
        return jdbc.queryForObject("SELECT deleted_at FROM files WHERE id = ?", Object.class, fileIdOf(url));
    }
}
