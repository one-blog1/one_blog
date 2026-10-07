package com.oneblog.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.oneblog.IntegrationTestSupport;

import jakarta.servlet.http.Cookie;

/** 대표 이미지 올리기·받기 (6.3, SEC-08 / quickstart 3, 4, 21, 22). */
class FileUploadIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private FileProperties fileProperties;

    private Cookie login;

    @BeforeEach
    void setUp() throws Exception {
        login = signUpAndLogin("uploader@example.com", "올리는사람");
    }

    private ResultActions upload(Cookie cookie, String name, String contentType, byte[] data) throws Exception {
        var request = multipart("/api/files/blog-cover")
                .file(new MockMultipartFile("file", name, contentType, data))
                .with(csrf());
        if (cookie != null) {
            request.cookie(cookie);
        }
        return mvc.perform(request);
    }

    @Test
    void 네_형식을_올리고_받을_수_있다() throws Exception {
        assertUploaded("a.jpg", "image/jpeg", TestImages.jpeg(), "jpg");
        assertUploaded("b.PNG", "image/png", TestImages.png(), "png");
        assertUploaded("c.gif", "image/gif", TestImages.gif(), "gif");
        assertUploaded("d.webp", "image/webp", TestImages.webp(), "webp");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM files WHERE purpose = 'BLOG_COVER'", Integer.class))
                .isEqualTo(4);
    }

    private void assertUploaded(String name, String type, byte[] data, String ext) throws Exception {
        String body = upload(login, name, type, data)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.url");
        assertThat(url).matches("^/files/[0-9a-f-]{36}\\." + ext + "$");
        String storedName = url.substring("/files/".length());
        assertThat(Files.exists(Path.of(fileProperties.storageDir(), storedName))).isTrue();
        assertThat(jdbc.queryForObject("SELECT original_name FROM files WHERE stored_name = ?", String.class,
                storedName)).isEqualTo(name);

        // 받을 때는 비회원도 되고, 형식 위장 방지 헤더가 붙는다
        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", type))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void 허용하지_않는_형식은_거부하고_남기지_않는다() throws Exception {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        upload(login, "x.svg", "image/svg+xml", svg)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        // 확장자만 바꾼 텍스트 파일
        upload(login, "fake.jpg", "image/jpeg", "#!/bin/sh\necho hi".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        // 내용은 PNG인데 확장자·타입은 jpg
        upload(login, "mismatch.jpg", "image/jpeg", TestImages.png())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        upload(login, "doc.pdf", "application/pdf", "%PDF-1.4".getBytes(StandardCharsets.US_ASCII))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM files", Integer.class)).isZero();
    }

    @Test
    void 크기가_3MB를_넘으면_거부한다() throws Exception {
        upload(login, "big.png", "image/png", TestImages.oversizedPng())
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM files", Integer.class)).isZero();
    }

    @Test
    void 빈_파일은_거부한다() throws Exception {
        upload(login, "empty.png", "image/png", new byte[0])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 로그인하지_않았거나_관리자면_올릴_수_없다() throws Exception {
        upload(null, "a.png", "image/png", TestImages.png())
                .andExpect(status().isUnauthorized());
        makeAdmin("uploader@example.com", "admin02");
        upload(login, "a.png", "image/png", TestImages.png())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_NOT_ALLOWED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM files", Integer.class)).isZero();
    }

    @Test
    void 없는_파일이나_이상한_이름은_404() throws Exception {
        mvc.perform(get("/files/00000000-0000-0000-0000-000000000000.png")).andExpect(status().isNotFound());
        mvc.perform(get("/files/build.gradle.kts")).andExpect(status().isNotFound());
    }
}
