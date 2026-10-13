package com.oneblog.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.jayway.jsonpath.JsonPath;
import com.oneblog.file.TestImages;

import jakarta.servlet.http.Cookie;

/** 글 목록 영상 썸네일과 미리보기 (BRD-02, 6.3, D-128). 영상 사이트 대신 가짜 VideoThumbnailFetcher를 쓴다. */
class VideoThumbnailIntegrationTest extends PostTestSupport {

    private static final String YOUTUBE = "https://youtu.be/dQw4w9WgXcQ";
    private static final String CHZZK_CLIP = "https://chzzk.naver.com/clips/AbC123xyz";

    @MockitoBean
    private VideoThumbnailFetcher fetcher;

    private Cookie owner;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUpAndLogin("thumb@example.com", "썸네일회원");
        createBlog(owner, "thumb-blog", "PUBLIC").andExpect(status().isCreated());
        when(fetcher.fetch(anyString())).thenReturn(Optional.empty());
        when(fetcher.fetch("youtube:dQw4w9WgXcQ")).thenReturn(Optional.of(TestImages.png()));
        when(fetcher.fetch("chzzk-clip:AbC123xyz")).thenReturn(Optional.of(TestImages.jpeg()));
    }

    private List<String> thumbs(Long postId, boolean deleted) {
        return jdbc.queryForList("SELECT original_name FROM files WHERE post_id = ? AND purpose = 'VIDEO_THUMB'"
                + " AND deleted_at IS " + (deleted ? "NOT NULL" : "NULL") + " ORDER BY id", String.class, postId);
    }

    private String uploadImage() throws Exception {
        String body = mvc.perform(multipart("/api/files/post-image")
                        .file(new MockMultipartFile("file", "a.png", "image/png", TestImages.png()))
                        .with(csrf()).cookie(owner))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.url");
    }

    private void edit(Long id, String content) throws Exception {
        mvc.perform(put("/api/posts/" + id).with(csrf()).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"고친 글\",\"content\":\"" + content + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 첫_미디어가_유튜브면_썸네일을_받아_목록과_og_image에_쓴다() throws Exception {
        Long id = writePost(owner, "thumb-blog", "영상 글", "오늘 본 영상이에요\\n\\n" + YOUTUBE + "\\n\\n재밌어요");

        assertThat(thumbs(id, false)).containsExactly("youtube:dQw4w9WgXcQ");
        String stored = jdbc.queryForObject("SELECT stored_name FROM files WHERE post_id = ? AND purpose = 'VIDEO_THUMB'",
                String.class, id);
        assertThat(stored).endsWith(".png");

        mvc.perform(get("/api/blogs/thumb-blog/posts"))
                .andExpect(jsonPath("$.items[0].thumbnailUrl").value("/files/" + stored))
                .andExpect(jsonPath("$.items[0].thumbnailVideo").value("youtube"))
                // 미리보기 글자에는 재생기가 끼지 않는다
                .andExpect(jsonPath("$.items[0].excerpt").value("오늘 본 영상이에요 재밌어요"));
        mvc.perform(get("/files/" + stored)).andExpect(status().isOk());

        String page = mvc.perform(get("/blog/thumb-blog/posts/" + id))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("/files/" + stored);
    }

    @Test
    void 같은_영상이면_다시_받지_않고_영상이_바뀌면_예전_썸네일을_지운다() throws Exception {
        Long id = writePost(owner, "thumb-blog", "영상 글", YOUTUBE);
        edit(id, YOUTUBE + "\\n\\n글을 조금 고침");
        verify(fetcher, times(1)).fetch("youtube:dQw4w9WgXcQ");
        assertThat(thumbs(id, false)).containsExactly("youtube:dQw4w9WgXcQ");

        edit(id, CHZZK_CLIP);
        assertThat(thumbs(id, false)).containsExactly("chzzk-clip:AbC123xyz");
        assertThat(thumbs(id, true)).containsExactly("youtube:dQw4w9WgXcQ");
        mvc.perform(get("/api/blogs/thumb-blog/posts"))
                .andExpect(jsonPath("$.items[0].thumbnailVideo").value("chzzk"));
    }

    @Test
    void 사진이_먼저_나오면_사진이_썸네일이고_영상_썸네일은_받지_않는다() throws Exception {
        String image = uploadImage();
        Long id = writePost(owner, "thumb-blog", "사진 먼저", "![](" + image + ")\\n\\n" + YOUTUBE);
        verify(fetcher, never()).fetch(anyString());
        assertThat(thumbs(id, false)).isEmpty();
        mvc.perform(get("/api/blogs/thumb-blog/posts"))
                .andExpect(jsonPath("$.items[0].thumbnailUrl").value(image))
                .andExpect(jsonPath("$.items[0].thumbnailVideo").isEmpty());
    }

    @Test
    void 영상을_빼거나_글을_지우면_썸네일은_지운_것으로_표시한다() throws Exception {
        Long id = writePost(owner, "thumb-blog", "영상 글", YOUTUBE);
        edit(id, "영상 없이 글자만");
        assertThat(thumbs(id, false)).isEmpty();
        assertThat(thumbs(id, true)).hasSize(1);

        Long other = writePost(owner, "thumb-blog", "지울 글", YOUTUBE);
        mvc.perform(delete("/api/posts/" + other).with(csrf()).cookie(owner)).andExpect(status().is2xxSuccessful());
        assertThat(thumbs(other, false)).isEmpty();
    }

    @Test
    void 받지_못했거나_사진이_아니면_썸네일_없이_글은_저장된다() throws Exception {
        when(fetcher.fetch("youtube:aaaaaaaaaaa")).thenReturn(Optional.of("<svg onload=alert(1)>".getBytes()));
        Long fake = writePost(owner, "thumb-blog", "가짜 사진", "https://youtu.be/aaaaaaaaaaa");
        Long missing = writePost(owner, "thumb-blog", "없는 영상", "https://youtu.be/bbbbbbbbbbb");
        assertThat(thumbs(fake, false)).isEmpty();
        assertThat(thumbs(missing, false)).isEmpty();
        mvc.perform(get("/api/blogs/thumb-blog/posts"))
                .andExpect(jsonPath("$.items[0].thumbnailUrl").isEmpty())
                .andExpect(jsonPath("$.items[1].thumbnailUrl").isEmpty());
    }

    @Test
    void 피드_카드에도_썸네일_미리보기_작성자_사진이_온다() throws Exception {
        writePost(owner, "thumb-blog", "피드 영상", "첫 줄\\n\\n" + YOUTUBE);
        mvc.perform(get("/api/feed?tab=latest"))
                .andExpect(jsonPath("$.items[0].thumbnailVideo").value("youtube"))
                .andExpect(jsonPath("$.items[0].excerpt").value("첫 줄"))
                // 프로필 사진을 올리지 않은 회원은 비어 있다 (화면이 기본 그림을 쓴다)
                .andExpect(jsonPath("$.items[0].authorImageUrl").isEmpty());
    }
}
