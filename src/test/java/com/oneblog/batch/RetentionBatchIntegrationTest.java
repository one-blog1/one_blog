package com.oneblog.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;
import com.oneblog.file.FileProperties;
import com.oneblog.file.TestImages;
import com.oneblog.post.PostTestSupport;

import jakarta.servlet.http.Cookie;

/**
 * 04:00 배치의 삭제는 정한 것만, 정한 때에 (4.5, D-60, D-86, 6.7, 029 US5).
 * 삭제는 되돌릴 수 없으므로 "29일째에는 남아 있다"와 "31일째에는 지워진다"를 함께 본다.
 */
class RetentionBatchIntegrationTest extends PostTestSupport {

    @Autowired
    private DailyBatch dailyBatch;

    @Autowired
    private FileProperties fileProperties;

    private Cookie owner;
    private Cookie member;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUpAndLogin("owner@example.com", "블로그장", "김주인", "010-6666-0001");
        member = signUpAndLogin("member@example.com", "멤버회원", "이멤버", "010-6666-0002");
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private boolean onDisk(String storedName) {
        return Files.exists(Path.of(fileProperties.storageDir()).toAbsolutePath().normalize().resolve(storedName));
    }

    private String storedName(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private String upload(Cookie who, String path) throws Exception {
        String body = mvc.perform(multipart(path)
                        .file(new MockMultipartFile("file", "a.png", "image/png", TestImages.png()))
                        .with(csrf()).cookie(who))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.url");
    }

    @Test
    void 탈퇴_회원의_개인정보는_30일_뒤_지운다() throws Exception {
        String photo = upload(member, "/api/files/profile-image");
        jdbc.update("UPDATE users SET profile_image_url = ?, bio = '소개' WHERE email = 'member@example.com'", photo);
        Long id = userId("member@example.com");
        mvc.perform(post("/api/me/withdrawal").with(csrf()).cookie(member).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());
        LocalDateTime now = LocalDateTime.now();

        dailyBatch.runAll(now.plusDays(29));
        assertThat(jdbc.queryForObject("SELECT name FROM users WHERE id = ?", String.class, id)).isEqualTo("이멤버");
        assertThat(onDisk(storedName(photo))).isTrue();

        dailyBatch.runAll(now.plusDays(31));
        assertThat(count("""
                SELECT COUNT(*) FROM users WHERE id = ? AND name IS NULL AND nickname IS NULL AND phone IS NULL
                  AND bio IS NULL AND profile_image_url IS NULL AND email IS NULL AND deleted_at IS NOT NULL""", id))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM files WHERE user_id = ? AND purpose = 'PROFILE'", id)).isZero();
        assertThat(onDisk(storedName(photo))).isFalse();
        // 탈퇴하지 않은 회원은 그대로
        assertThat(jdbc.queryForObject("SELECT name FROM users WHERE email = 'owner@example.com'", String.class))
                .isEqualTo("김주인");
    }

    @Test
    void 폐쇄된_블로그는_30일_뒤_내용을_지우고_주소를_비운다() throws Exception {
        postBlog(owner, """
                {"name":"닫을 블로그","slug":"closing-blog","visibility":"PUBLIC","joinPolicy":"OPEN","tags":["여행"]}
                """).andExpect(status().isCreated());
        addMember("closing-blog", "member@example.com", "MEMBER");
        Cookie subscriber = signUpAndLogin("sub@example.com", "구독자", "박구독", "010-6666-0003");
        Cookie kicked = signUpAndLogin("kicked@example.com", "강퇴회원", "최강퇴", "010-6666-0004");
        addMember("closing-blog", "kicked@example.com", "MEMBER");
        mvc.perform(post("/api/blogs/closing-blog/subscription").with(csrf()).cookie(subscriber))
                .andExpect(status().isOk());
        mvc.perform(post("/api/blogs/closing-blog/categories").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"분류\"}"))
                .andExpect(status().isCreated());
        String photo = upload(member, "/api/files/post-image");
        Long postId = writePost(member, "closing-blog", "사진 글", "![](" + photo + ")");
        mvc.perform(post("/api/posts/" + postId + "/comments").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"댓글\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/blogs/closing-blog/members/" + userId("kicked@example.com") + "/sanctions").with(csrf())
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"KICK\",\"reason\":\"규칙 위반\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/blogs/closing-blog/blacklist-inquiries").with(csrf()).cookie(kicked)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"풀어 주세요\"}"))
                .andExpect(status().isCreated());
        Long blogId = blogId("closing-blog");

        mvc.perform(post("/api/blogs/closing-blog/close").with(csrf()).cookie(owner)).andExpect(status().isOk());
        LocalDateTime scheduled = jdbc.queryForObject("SELECT close_scheduled_at FROM blogs WHERE id = ?",
                LocalDateTime.class, blogId);
        LocalDateTime closedAt = scheduled.plusMinutes(1);
        dailyBatch.runAll(closedAt);
        assertThat(jdbc.queryForObject("SELECT status FROM blogs WHERE id = ?", String.class, blogId))
                .isEqualTo("CLOSED");

        // 29일째: 내용이 그대로이고, 주소는 아직 다른 사람이 쓸 수 없다
        dailyBatch.runAll(closedAt.plusDays(29));
        assertThat(count("SELECT COUNT(*) FROM posts WHERE blog_id = ?", blogId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM blog_blacklists WHERE blog_id = ?", blogId)).isEqualTo(1);
        assertThat(onDisk(storedName(photo))).isTrue();
        Cookie other = signUpAndLogin("other@example.com", "다른회원", "정다른", "010-6666-0005");
        createBlog(other, "closing-blog", "PUBLIC").andExpect(status().isConflict());

        // 31일째: 글·댓글·카테고리·블로그 태그·구독·사진·블랙리스트·해제 문의를 지우고 주소를 비운다
        dailyBatch.runAll(closedAt.plusDays(31));
        assertThat(count("SELECT COUNT(*) FROM posts WHERE blog_id = ?", blogId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM comments WHERE post_id = ?", postId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM categories WHERE blog_id = ?", blogId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM blog_tags WHERE blog_id = ?", blogId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM blog_subscriptions WHERE blog_id = ?", blogId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM files WHERE stored_name = ?", storedName(photo))).isZero();
        assertThat(onDisk(storedName(photo))).isFalse();
        assertThat(count("SELECT COUNT(*) FROM blog_blacklists WHERE blog_id = ?", blogId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM blacklist_inquiries WHERE blog_id = ?", blogId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM blogs WHERE id = ? AND slug IS NULL AND deleted_at IS NOT NULL", blogId))
                .isEqualTo(1);
        // 31일 뒤로 돌린 배치가 30분 무활동 로그인을 지웠으므로 다시 로그인한다
        other = loginCookie("other@example.com");
        createBlog(other, "closing-blog", "PUBLIC").andExpect(status().isCreated());
    }

    @Test
    void 따로_지운_댓글과_파일은_30일_뒤_지운다() throws Exception {
        createBlog(owner, "keep-blog", "PUBLIC").andExpect(status().isCreated());
        addMember("keep-blog", "member@example.com", "MEMBER");
        Long postId = writePost(member, "keep-blog", "남는 글", "본문");
        Long lonely = commentId(owner, postId, "답글 없는 댓글", null);
        Long parent = commentId(owner, postId, "답글 있는 댓글", null);
        commentId(member, postId, "살아 있는 답글", parent);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/comments/" + lonely).with(csrf()).cookie(owner))
                .andExpect(status().isNoContent());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/comments/" + parent).with(csrf()).cookie(owner))
                .andExpect(status().isNoContent());

        // 글에 붙지 않은 업로드, 지운 표시가 된 사진, 쓰지 않는 배너 사진
        String orphan = upload(member, "/api/files/post-image");
        String dropped = upload(member, "/api/files/post-image");
        jdbc.update("UPDATE files SET deleted_at = NOW(6) WHERE stored_name = ?", storedName(dropped));
        Cookie admin = createAdminAndLogin("admin01");
        String banner = upload(admin, "/api/admin/banners/image");
        LocalDateTime now = LocalDateTime.now();

        dailyBatch.runAll(now.plusDays(29));
        assertThat(count("SELECT COUNT(*) FROM comments WHERE id IN (?, ?)", lonely, parent)).isEqualTo(2);
        for (String url : new String[] {orphan, dropped, banner}) {
            assertThat(count("SELECT COUNT(*) FROM files WHERE stored_name = ?", storedName(url))).isEqualTo(1);
            assertThat(onDisk(storedName(url))).isTrue();
        }

        dailyBatch.runAll(now.plusDays(31));
        assertThat(count("SELECT COUNT(*) FROM comments WHERE id = ?", lonely)).isZero();
        assertThat(count("SELECT COUNT(*) FROM comments WHERE id = ?", parent)).as("살아 있는 답글이 달린 댓글은 남는다")
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM comments WHERE parent_id = ?", parent)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM posts WHERE id = ?", postId)).isEqualTo(1);
        for (String url : new String[] {orphan, dropped, banner}) {
            assertThat(count("SELECT COUNT(*) FROM files WHERE stored_name = ?", storedName(url))).isZero();
            assertThat(onDisk(storedName(url))).isFalse();
        }
    }

    private Long commentId(Cookie who, Long postId, String content, Long parentId) throws Exception {
        String json = parentId == null ? "{\"content\":\"" + content + "\"}"
                : "{\"content\":\"" + content + "\",\"parentId\":" + parentId + "}";
        String body = mvc.perform(post("/api/posts/" + postId + "/comments").with(csrf()).cookie(who)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void 신고_제재_관리자_기록은_1년이_지난_것만_지운다() throws Exception {
        createBlog(owner, "record-blog", "PUBLIC").andExpect(status().isCreated());
        addMember("record-blog", "member@example.com", "MEMBER");
        Long postA = writePost(member, "record-blog", "글 하나", "본문");
        Long postB = writePost(member, "record-blog", "글 둘", "본문");
        Cookie reporter = signUpAndLogin("r@example.com", "신고자", "정신고", "010-6666-0006");
        for (Long target : new Long[] {postA, postB}) {
            mvc.perform(post("/api/reports").with(csrf()).cookie(reporter).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"targetType\":\"POST\",\"targetId\":" + target + ",\"reason\":\"SPAM\"}"))
                    .andExpect(status().isCreated());
        }
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/blogs/record-blog/members/" + userId("member@example.com") + "/sanctions").with(csrf())
                            .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"WARNING\",\"reason\":\"경고\"}"))
                    .andExpect(status().isNoContent());
        }
        Cookie admin = createAdminAndLogin("admin01");
        for (Long target : new Long[] {postA, postB}) {
            mvc.perform(post("/api/admin/posts/" + target + "/hide").with(csrf()).cookie(admin)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"hidden\":true}"))
                    .andExpect(status().isNoContent());
        }
        LocalDateTime now = LocalDateTime.now();
        for (String table : new String[] {"reports", "sanctions", "admin_actions"}) {
            Long first = jdbc.queryForObject("SELECT MIN(id) FROM " + table, Long.class);
            jdbc.update("UPDATE " + table + " SET created_at = ? WHERE id = ?", now.minusDays(366), first);
            jdbc.update("UPDATE " + table + " SET created_at = ? WHERE id <> ?", now.minusDays(364), first);
        }

        dailyBatch.runAll(now);
        for (String table : new String[] {"reports", "sanctions", "admin_actions"}) {
            assertThat(count("SELECT COUNT(*) FROM " + table + " WHERE created_at < ?", now.minusDays(365)))
                    .as(table + "의 1년 지난 기록").isZero();
            assertThat(count("SELECT COUNT(*) FROM " + table)).as(table + "의 1년 안 기록").isPositive();
        }
    }

    @Test
    void 보관_기간이_지난_알림과_쓴_인증번호는_배치가_지운다() throws Exception {
        // 6.7 알림 보관(30일 기본, 7일 선택). 인증번호·토큰은 4.5에서 "쓰면 바로 삭제"지만 지금 코드는
        // 쓴 가입 인증번호와 이메일 찾기 토큰을 다음 04:00 배치에서 지운다 (요구사항과 다름, 029 분석 6번)
        Cookie seven = signUpAndLogin("seven@example.com", "칠일회원", "한칠일", "010-6666-0007");
        jdbc.update("UPDATE users SET notification_retention_days = 7 WHERE email = 'seven@example.com'");
        mvc.perform(post("/api/users/블로그장/follow").with(csrf()).cookie(member)).andExpect(status().isOk());
        mvc.perform(post("/api/users/블로그장/follow").with(csrf()).cookie(seven)).andExpect(status().isOk());
        mvc.perform(post("/api/users/칠일회원/follow").with(csrf()).cookie(member)).andExpect(status().isOk());
        Long ownerId = userId("owner@example.com");
        Long sevenId = userId("seven@example.com");
        LocalDateTime now = LocalDateTime.now();
        // 블로그장(30일): 31일 전 1건, 29일 전 1건. 칠일회원(7일): 8일 전 1건
        Long ownerFirst = jdbc.queryForObject("SELECT MIN(id) FROM notifications WHERE user_id = ?", Long.class, ownerId);
        jdbc.update("UPDATE notifications SET created_at = ? WHERE id = ?", now.minusDays(31), ownerFirst);
        jdbc.update("UPDATE notifications SET created_at = ? WHERE user_id = ? AND id <> ?", now.minusDays(29), ownerId,
                ownerFirst);
        jdbc.update("UPDATE notifications SET created_at = ? WHERE user_id = ?", now.minusDays(8), sevenId);

        String found = mvc.perform(post("/api/auth/find-email").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"이멤버\",\"phone\":\"010-6666-0002\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((Object) JsonPath.read(found, "$[0].token")).isNotNull();
        assertThat(count("SELECT COUNT(*) FROM verification_codes WHERE used_at IS NOT NULL")).isPositive();

        dailyBatch.runAll(now);
        assertThat(count("SELECT COUNT(*) FROM notifications WHERE user_id = ?", ownerId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM notifications WHERE user_id = ?", sevenId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM verification_codes WHERE used_at IS NOT NULL")).isZero();
        assertThat(count("SELECT COUNT(*) FROM account_lookup_tokens")).as("아직 쓸 수 있는 토큰은 남는다").isEqualTo(1);

        dailyBatch.runAll(now.plusMinutes(11));
        assertThat(count("SELECT COUNT(*) FROM account_lookup_tokens")).as("10분이 지나 만료된 토큰").isZero();
    }
}
