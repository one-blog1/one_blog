package com.oneblog.banner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.oneblog.file.TestImages;
import com.oneblog.post.PostTestSupport;

import jakarta.servlet.http.Cookie;

/** 025 메인 배너 (BRD-12, ADM-08, 6.8, D-132, D-133): 인기 글·새 블로그·인기 블로그는 자동, 광고·공지·이벤트는 관리자, 종류를 번갈아 보여 준다. */
class BannerIntegrationTest extends PostTestSupport {

    private Cookie owner;
    private Cookie reader;
    private Cookie admin;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUpAndLogin("banner-owner@example.com", "배너주인");
        reader = signUpAndLogin("banner-reader@example.com", "배너독자");
        admin = createAdminAndLogin("admin01");
        createBlog(owner, "banner-blog", "PUBLIC").andExpect(status().isCreated());
        createBlog(owner, "secret-blog", "PRIVATE").andExpect(status().isCreated());
    }

    private String uploadPostImage() throws Exception {
        String body = mvc.perform(multipart("/api/files/post-image")
                        .file(new MockMultipartFile("file", "a.png", "image/png", TestImages.png()))
                        .with(csrf()).cookie(owner))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.url");
    }

    private Long photoPost(String slug, String title) throws Exception {
        return writePost(owner, slug, title, "![](" + uploadPostImage() + ")\\n\\n사진 글");
    }

    private void like(Long postId, Cookie who) throws Exception {
        mvc.perform(post("/api/posts/" + postId + "/like").with(csrf()).cookie(who)).andExpect(status().isOk());
    }

    private Long uploadBannerImage() throws Exception {
        String body = mvc.perform(multipart("/api/admin/banners/image")
                        .file(new MockMultipartFile("file", "ad.jpg", "image/jpeg", TestImages.jpeg()))
                        .with(csrf()).cookie(admin))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.fileId")).longValue();
    }

    private ResultActions saveAd(String method, String url, String json) throws Exception {
        var builder = "PUT".equals(method) ? put(url) : post(url);
        return mvc.perform(builder.with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long createAd(String title, String link, boolean visible) throws Exception {
        return createBanner("AD", title, link, visible);
    }

    private Long createBanner(String kind, String title, String link, boolean visible) throws Exception {
        String body = saveAd("POST", "/api/admin/banners", """
                {"kind":"%s","title":"%s","caption":"광고주","linkUrl":"%s","imageFileId":%d,"visible":%s}
                """.formatted(kind, title, link, uploadBannerImage(), visible))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void settings(String json) throws Exception {
        mvc.perform(put("/api/admin/banner-settings").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    /** 자동 종류 중 인기 글만 켠다. */
    private void popularOnly(int count, int days) throws Exception {
        settings("""
                {"popular":{"count":%d,"days":%d},"newBlog":{"count":0,"days":30},"popularBlog":{"count":0,"days":30}}
                """.formatted(count, days));
    }

    @Test
    void 보여_줄_것이_없으면_빈_목록이고_누구나_본다() throws Exception {
        popularOnly(0, 7);
        mvc.perform(get("/api/banners")).andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void 인기_글은_최근_공개_글_중_썸네일이_있는_글을_좋아요_순으로_정한_개수만큼() throws Exception {
        popularOnly(3, 7);
        Long top = photoPost("banner-blog", "좋아요 많은 글");
        Long second = photoPost("banner-blog", "좋아요 적은 글");
        Long noLike = photoPost("banner-blog", "좋아요 없는 글");
        Long noPhoto = writePost(owner, "banner-blog", "사진 없는 글", "글자만");
        Long old = photoPost("banner-blog", "오래된 글");
        Long secret = photoPost("secret-blog", "비공개 블로그 글");
        like(top, reader);
        like(top, owner);
        like(second, reader);
        like(noPhoto, reader);
        like(old, reader);
        like(secret, owner); // 비공개 블로그는 멤버만 누를 수 있다
        jdbc.update("UPDATE posts SET created_at = NOW() - INTERVAL 10 DAY WHERE id = ?", old);

        // 좋아요가 없는 글도 개수를 채운다 (D-133). 사진 없는 글·기간 밖 글·비공개 블로그 글은 빠진다
        mvc.perform(get("/api/banners"))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].kind").value("POPULAR"))
                .andExpect(jsonPath("$.items[0].label").value("이번 주 인기"))
                .andExpect(jsonPath("$.items[0].title").value("좋아요 많은 글"))
                .andExpect(jsonPath("$.items[0].caption").value("블로그 banner-blog · 좋아요 2"))
                .andExpect(jsonPath("$.items[0].linkUrl").value("/blog/banner-blog/posts/" + top))
                .andExpect(jsonPath("$.items[0].external").value(false))
                .andExpect(jsonPath("$.items[1].title").value("좋아요 적은 글"))
                .andExpect(jsonPath("$.items[2].linkUrl").value("/blog/banner-blog/posts/" + noLike));

        // 기간을 30일로 넓히면 오래된 글도 들어온다
        popularOnly(5, 30);
        mvc.perform(get("/api/banners"))
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.items[0].label").value("이번 달 인기"));

        // 차단한 회원의 글은 그 사람에게만 빠진다
        mvc.perform(post("/api/users/배너주인/block").with(csrf()).cookie(reader)).andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/banners").cookie(reader)).andExpect(jsonPath("$.items", hasSize(0)));

        // 0개면 인기 글을 넣지 않는다
        popularOnly(0, 7);
        mvc.perform(get("/api/banners")).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void 새_블로그와_인기_블로그는_자동이고_같은_블로그는_한_번만() throws Exception {
        settings("""
                {"popular":{"count":0,"days":7},"newBlog":{"count":2,"days":30},"popularBlog":{"count":2,"days":30}}
                """);
        createBlog(owner, "old-blog", "PUBLIC").andExpect(status().isCreated());
        jdbc.update("UPDATE blogs SET created_at = NOW() - INTERVAL 60 DAY, description = '오래된 블로그' WHERE slug = 'old-blog'");
        addMember("old-blog", "banner-reader@example.com", "MEMBER");
        addMember("banner-blog", "banner-reader@example.com", "MEMBER");

        // banner-blog는 새 블로그이면서 새 멤버도 있지만 새 블로그로 한 번만 나온다. 비공개 블로그는 빠진다
        mvc.perform(get("/api/banners"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].kind").value("NEW_BLOG"))
                .andExpect(jsonPath("$.items[0].label").value("새 블로그"))
                .andExpect(jsonPath("$.items[0].title").value("블로그 banner-blog"))
                .andExpect(jsonPath("$.items[0].blogSlug").value("banner-blog"))
                .andExpect(jsonPath("$.items[0].linkUrl").value("/blog/banner-blog"))
                .andExpect(jsonPath("$.items[0].imageUrl").isEmpty())
                .andExpect(jsonPath("$.items[1].kind").value("POPULAR_BLOG"))
                .andExpect(jsonPath("$.items[1].label").value("인기 블로그"))
                .andExpect(jsonPath("$.items[1].caption").value("멤버 2명 · 오래된 블로그"));
    }

    @Test
    void 종류를_하나씩_번갈아_놓는다() throws Exception {
        settings("""
                {"popular":{"count":3,"days":7},"newBlog":{"count":2,"days":30},"popularBlog":{"count":0,"days":30}}
                """);
        Long post = photoPost("banner-blog", "인기 글");
        like(post, reader);
        createBanner("AD", "광고", "https://example.com/ad", true);
        createBanner("NOTICE", "공지", "/notice.html", true);
        createBanner("EVENT", "이벤트", "/blog/banner-blog", true);

        mvc.perform(get("/api/banners"))
                .andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.items[0].kind").value("POPULAR"))
                .andExpect(jsonPath("$.items[1].kind").value("AD"))
                .andExpect(jsonPath("$.items[2].kind").value("NEW_BLOG"))
                .andExpect(jsonPath("$.items[3].kind").value("NOTICE"))
                .andExpect(jsonPath("$.items[3].label").value("공지"))
                .andExpect(jsonPath("$.items[4].kind").value("EVENT"))
                .andExpect(jsonPath("$.items[4].label").value("이벤트"));
        saveAd("POST", "/api/admin/banners", """
                {"kind":"POPUP","title":"x","linkUrl":"https://example.com","imageFileId":%d}
                """.formatted(uploadBannerImage()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("kind"));
    }

    @Test
    void 인기_글과_광고를_번갈아_놓고_노출을_끈_광고는_빠진다() throws Exception {
        popularOnly(3, 7);
        Long post = photoPost("banner-blog", "인기 글");
        like(post, reader);
        createAd("첫 광고", "https://example.com/sale", true);
        createAd("꺼진 광고", "https://example.com/off", false);
        createAd("둘째 광고", "/blog/banner-blog", true);

        mvc.perform(get("/api/banners"))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].kind").value("POPULAR"))
                .andExpect(jsonPath("$.items[1].kind").value("AD"))
                .andExpect(jsonPath("$.items[1].label").value("광고"))
                .andExpect(jsonPath("$.items[1].title").value("첫 광고"))
                .andExpect(jsonPath("$.items[1].caption").value("광고주"))
                .andExpect(jsonPath("$.items[1].external").value(true))
                .andExpect(jsonPath("$.items[2].title").value("둘째 광고"))
                .andExpect(jsonPath("$.items[2].external").value(false));
        String image = JsonPath.read(mvc.perform(get("/api/banners")).andReturn().getResponse().getContentAsString(),
                "$.items[1].imageUrl");
        mvc.perform(get(image)).andExpect(status().isOk());
    }

    @Test
    void 사진에서_보일_자리를_정해_배너에_그대로_보낸다() throws Exception {
        popularOnly(0, 7);
        settings("""
                {"newBlog":{"count":0,"days":30},"popularBlog":{"count":0,"days":30}}
                """);
        String body = saveAd("POST", "/api/admin/banners", """
                {"kind":"EVENT","title":"이벤트","linkUrl":"/notice.html","imageFileId":%d,"imagePosX":10,"imagePosY":90}
                """.formatted(uploadBannerImage()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        mvc.perform(get("/api/banners"))
                .andExpect(jsonPath("$.items[0].imagePosX").value(10))
                .andExpect(jsonPath("$.items[0].imagePosY").value(90));

        // 빼면 가운데, 0~100 밖이면 거부
        Long image = jdbc.queryForObject("SELECT image_file_id FROM banners WHERE id = ?", Long.class, id);
        saveAd("PUT", "/api/admin/banners/" + id, """
                {"kind":"EVENT","title":"이벤트","linkUrl":"/notice.html","imageFileId":%d,"imagePosX":101}
                """.formatted(image))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("imagePosX"));
        saveAd("PUT", "/api/admin/banners/" + id, """
                {"kind":"EVENT","title":"이벤트","linkUrl":"/notice.html","imageFileId":%d}
                """.formatted(image))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/banners").cookie(admin))
                .andExpect(jsonPath("$.banners[0].imagePosX").value(50))
                .andExpect(jsonPath("$.banners[0].imagePosY").value(50));
    }

    @Test
    void 광고_링크와_사진을_검사한다() throws Exception {
        Long image = uploadBannerImage();
        for (String bad : new String[] {"javascript:alert(1)", "//evil.example.com", "http://", "ftp://example.com",
                "/a b", "https://user:pw@example.com"}) {
            saveAd("POST", "/api/admin/banners", """
                    {"title":"광고","linkUrl":"%s","imageFileId":%d}
                    """.formatted(bad, image))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("linkUrl"));
        }
        // 글 사진(POST)은 배너 사진으로 쓸 수 없다
        String postImage = uploadPostImage();
        Long postFileId = jdbc.queryForObject("SELECT id FROM files WHERE stored_name = ?", Long.class,
                postImage.substring("/files/".length()));
        saveAd("POST", "/api/admin/banners", """
                {"title":"광고","linkUrl":"https://example.com","imageFileId":%d}
                """.formatted(postFileId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("imageFileId"));
        saveAd("POST", "/api/admin/banners", "{\"title\":\" \",\"linkUrl\":\"https://example.com\",\"imageFileId\":" + image + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
        mvc.perform(put("/api/admin/banner-settings").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"popular\":{\"count\":6,\"days\":3}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("popular.count"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("popular.days"));
    }

    @Test
    void 회원은_관리자_배너_API를_쓸_수_없다() throws Exception {
        mvc.perform(get("/api/admin/banners").cookie(owner)).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/admin/banners/image")
                        .file(new MockMultipartFile("file", "ad.png", "image/png", TestImages.png()))
                        .with(csrf()).cookie(owner))
                .andExpect(status().isForbidden());
    }

    @Test
    void 고치고_옮기고_지우면_사진과_활동_기록이_따라간다() throws Exception {
        Long first = createAd("첫 광고", "https://example.com/1", true);
        Long second = createAd("둘째 광고", "https://example.com/2", true);
        Long oldImage = jdbc.queryForObject("SELECT image_file_id FROM banners WHERE id = ?", Long.class, first);

        Long newImage = uploadBannerImage();
        saveAd("PUT", "/api/admin/banners/" + first, """
                {"title":"고친 광고","caption":"","linkUrl":"https://example.com/new","imageFileId":%d,"visible":false}
                """.formatted(newImage)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM files WHERE id = ?", Boolean.class, oldImage))
                .isTrue();
        // 다른 광고의 사진은 가져다 쓸 수 없다
        saveAd("PUT", "/api/admin/banners/" + second, """
                {"title":"둘째 광고","linkUrl":"https://example.com/2","imageFileId":%d}
                """.formatted(newImage)).andExpect(status().isBadRequest());

        mvc.perform(post("/api/admin/banners/" + second + "/move").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"direction\":\"up\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/banners").cookie(admin))
                .andExpect(jsonPath("$.banners[0].id").value(second.intValue()))
                .andExpect(jsonPath("$.banners[1].title").value("고친 광고"))
                .andExpect(jsonPath("$.banners[1].kind").value("AD"))
                .andExpect(jsonPath("$.banners[1].kindLabel").value("광고"))
                .andExpect(jsonPath("$.banners[1].caption").isEmpty())
                .andExpect(jsonPath("$.banners[1].visible").value(false))
                .andExpect(jsonPath("$.settings.popular.count").value(3))
                .andExpect(jsonPath("$.settings.popular.days").value(7))
                .andExpect(jsonPath("$.settings.newBlog.count").value(2))
                .andExpect(jsonPath("$.settings.popularBlog.days").value(30))
                .andExpect(jsonPath("$.autoPreview.newBlog[0].title").value("블로그 banner-blog"))
                .andExpect(jsonPath("$.maxVisibleBanners").value(8));

        mvc.perform(delete("/api/admin/banners/" + first).with(csrf()).cookie(admin)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM files WHERE id = ?", Boolean.class, newImage))
                .isTrue();
        assertThat(jdbc.queryForList("SELECT action_type FROM admin_actions WHERE target_type = 'BANNER' ORDER BY id",
                String.class)).containsExactly("BANNER_CREATE", "BANNER_CREATE", "BANNER_UPDATE", "BANNER_MOVE",
                "BANNER_DELETE");
        mvc.perform(delete("/api/admin/banners/" + first).with(csrf()).cookie(admin)).andExpect(status().isNotFound());
    }
}
