package com.oneblog.blog.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.oneblog.batch.DailyBatch;
import com.oneblog.post.PostTestSupport;

import jakarta.servlet.http.Cookie;

/** 블로그 운영: 정보 수정·부블로그장·탈퇴·위임·폐쇄·04:00 배치 (BLG-01, BLG-07~09, D-71 / specs/012-blog-ops). */
class BlogOpsIntegrationTest extends PostTestSupport {

    @Autowired
    private DailyBatch dailyBatch;

    private Cookie owner;
    private Cookie member;
    private Cookie subscriber;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUpAndLogin("owner@example.com", "블로그장");
        member = signUpAndLogin("member@example.com", "멤버회원");
        subscriber = signUpAndLogin("sub@example.com", "구독자");
        createBlog(owner, "open-blog", "PUBLIC").andExpect(status().isCreated());
        addMember("open-blog", "member@example.com", "MEMBER");
        mvc.perform(post("/api/blogs/open-blog/subscription").with(csrf()).cookie(subscriber));
    }

    private Long memberId() {
        return userId("member@example.com");
    }

    @Test
    void 블로그장은_멤버별_글_댓글_경고_수를_보고_그_목록으로_들어간다() throws Exception {
        Long postId = writePost(member, "open-blog", "멤버의 글", "본문");
        mvc.perform(post("/api/posts/" + postId + "/comments").with(csrf()).cookie(member)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"멤버의 댓글\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/blogs/open-blog/members/" + memberId() + "/sanctions").with(csrf()).cookie(owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"WARNING\",\"reason\":\"도배\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/blogs/open-blog/member-stats").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[1].nickname").value("멤버회원"))
                .andExpect(jsonPath("$.items[1].postCount").value(1))
                .andExpect(jsonPath("$.items[1].commentCount").value(1))
                .andExpect(jsonPath("$.items[1].warningCount").value(1));
        mvc.perform(get("/api/blogs/open-blog/members/" + memberId() + "/posts").cookie(owner))
                .andExpect(jsonPath("$.items[0].title").value("멤버의 글"));
        mvc.perform(get("/api/blogs/open-blog/members/" + memberId() + "/comments").cookie(owner))
                .andExpect(jsonPath("$.items[0].content").value("멤버의 댓글"))
                .andExpect(jsonPath("$.items[0].postId").value(postId.intValue()));

        // 블로그장 전용: 멤버 관리 권한을 받은 부블로그장도 못 본다 (D-112)
        mvc.perform(put("/api/blogs/open-blog/members/" + memberId() + "/sub-owner").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subOwner\":true,\"canEditInfo\":false,\"canManageMembers\":true,\"canManagePosts\":false}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/blogs/open-blog/member-stats").cookie(member)).andExpect(status().isForbidden());
        mvc.perform(get("/api/blogs/open-blog/members/" + memberId() + "/comments").cookie(member))
                .andExpect(status().isForbidden());
    }

    private int notifications(String email, String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications n JOIN users u ON u.id = n.user_id "
                + "WHERE u.email = ? AND n.type = ?", Integer.class, email, type);
    }

    @Test
    void 정보는_정보_수정_권한이_있어야_고친다() throws Exception {
        String body = "{\"description\":\"새 소개\",\"tags\":[\"새태그\"]}";
        mvc.perform(put("/api/blogs/open-blog/info").with(csrf()).cookie(member)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/blogs/open-blog/members/" + memberId() + "/sub-owner").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subOwner\":true,\"canEditInfo\":true,\"canManageMembers\":false,\"canManagePosts\":false}"))
                .andExpect(status().isNoContent());
        mvc.perform(put("/api/blogs/open-blog/info").with(csrf()).cookie(member)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        mvc.perform(get("/api/blogs/open-blog"))
                .andExpect(jsonPath("$.description").value("새 소개"))
                .andExpect(jsonPath("$.tags[0]").value("새태그"));
        // 블로그 이름은 바꿀 수 없다 (D-109)
        mvc.perform(put("/api/blogs/open-blog/info").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"새 이름\",\"description\":\"새 소개\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
        // 부블로그장도 공개 범위는 못 바꾼다 (블로그장만)
        mvc.perform(put("/api/blogs/open-blog/settings").with(csrf()).cookie(member)
                .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isForbidden());
        // 멤버 정보는 멤버 관리 권한이 있어야 본다
        mvc.perform(get("/api/blogs/open-blog/members").cookie(member)).andExpect(status().isForbidden());
        mvc.perform(get("/api/blogs/open-blog/members").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[1].role").value("SUB_OWNER"))
                .andExpect(jsonPath("$[1].email").value(containsString("*")));
    }

    @Test
    void 비공개로_바꾸면_멤버가_아닌_구독자에게_알린다() throws Exception {
        mvc.perform(put("/api/blogs/open-blog/settings").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PRIVATE\",\"joinPolicy\":\"APPROVAL\"}"))
                .andExpect(status().isNoContent());
        assertThat(notifications("sub@example.com", "BLOG_PRIVATE")).isEqualTo(1);
        assertThat(notifications("member@example.com", "BLOG_PRIVATE")).isZero();
        mvc.perform(get("/api/blogs/open-blog").cookie(subscriber)).andExpect(status().isForbidden());

        // 공개 블로그 3개를 가진 상태에서 비공개를 공개로 바꾸면 개수 제한에 걸린다 (BLG-10)
        createBlog(owner, "pub-two", "PUBLIC").andExpect(status().isCreated());
        createBlog(owner, "pub-three", "PUBLIC").andExpect(status().isCreated());
        createBlog(owner, "link-four", "UNLISTED").andExpect(status().isCreated());
        mvc.perform(put("/api/blogs/open-blog/settings").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BLOG_LIMIT_EXCEEDED"));
    }

    @Test
    void 블로그를_나가면_글은_탈퇴한_계정이_되고_블로그장은_못_나간다() throws Exception {
        Long postId = writePost(member, "open-blog", "멤버 글", "본문");
        mvc.perform(delete("/api/blogs/open-blog/membership").with(csrf()).cookie(owner))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/blogs/open-blog/membership").with(csrf()).cookie(member))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.authorName").value("탈퇴한 계정"));
        mvc.perform(get("/api/blogs/open-blog")).andExpect(jsonPath("$.memberCount").value(1));
    }

    @Test
    void 위임을_수락하면_블로그장이_바뀐다() throws Exception {
        mvc.perform(post("/api/blogs/open-blog/transfer").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"toUserId\":" + userId("sub@example.com") + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/blogs/open-blog/transfer").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"toUserId\":" + memberId() + "}"))
                .andExpect(status().isOk());
        assertThat(notifications("member@example.com", "TRANSFER_REQUEST")).isEqualTo(1);
        String body = mvc.perform(get("/api/me/transfer-requests").cookie(member))
                .andExpect(jsonPath("$[0].blogSlug").value("open-blog"))
                .andReturn().getResponse().getContentAsString();
        Integer id = com.jayway.jsonpath.JsonPath.read(body, "$[0].id");

        // 다른 사람은 수락할 수 없다
        mvc.perform(post("/api/transfer-requests/" + id + "/accept").with(csrf()).cookie(subscriber))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/transfer-requests/" + id + "/accept").with(csrf()).cookie(member))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/blogs/open-blog").cookie(member)).andExpect(jsonPath("$.myRole").value("OWNER"));
        mvc.perform(get("/api/blogs/open-blog").cookie(owner)).andExpect(jsonPath("$.myRole").value("MEMBER"));
        assertThat(notifications("owner@example.com", "TRANSFER_RESULT")).isEqualTo(1);
    }

    @Test
    void 폐쇄를_예약하고_철회하고_배치가_폐쇄한다() throws Exception {
        mvc.perform(post("/api/blogs/open-blog/transfer").with(csrf()).cookie(owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"toUserId\":" + memberId() + "}"));
        mvc.perform(post("/api/blogs/open-blog/close").with(csrf()).cookie(member)).andExpect(status().isForbidden());
        mvc.perform(post("/api/blogs/open-blog/close").with(csrf()).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closeScheduledAt").exists());
        assertThat(notifications("member@example.com", "BLOG_CLOSING")).isEqualTo(1);
        assertThat(notifications("sub@example.com", "BLOG_CLOSING")).isEqualTo(1);
        assertThat(notifications("owner@example.com", "BLOG_CLOSING")).isZero();
        // 대기 중인 위임 요청은 취소되고, 폐쇄 예정 중에는 위임할 수 없다
        assertThat(jdbc.queryForObject("SELECT status FROM blog_transfer_requests", String.class)).isEqualTo("CANCELED");
        mvc.perform(post("/api/blogs/open-blog/transfer").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"toUserId\":" + memberId() + "}"))
                .andExpect(status().isConflict());
        // 폐쇄 예정 중에도 평소처럼 글을 쓴다 (D-67)
        writePost(member, "open-blog", "폐쇄 전 글", "본문");

        mvc.perform(delete("/api/blogs/open-blog/close").with(csrf()).cookie(owner)).andExpect(status().isNoContent());
        assertThat(notifications("member@example.com", "BLOG_CLOSE_CANCELED")).isEqualTo(1);
        mvc.perform(get("/api/blogs/open-blog")).andExpect(jsonPath("$.status").value("ACTIVE"));

        mvc.perform(post("/api/blogs/open-blog/close").with(csrf()).cookie(owner)).andExpect(status().isOk());
        assertThat(notifications("member@example.com", "BLOG_CLOSING")).isEqualTo(2);
        LocalDateTime scheduled = jdbc.queryForObject("SELECT close_scheduled_at FROM blogs WHERE slug = 'open-blog'",
                LocalDateTime.class);

        // 3일 전 알림, 그리고 예정 시각이 지나면 폐쇄
        dailyBatch.runAll(scheduled.minusDays(3));
        assertThat(notifications("member@example.com", "BLOG_CLOSING")).isEqualTo(3);
        dailyBatch.runAll(scheduled.minusDays(3));
        assertThat(notifications("member@example.com", "BLOG_CLOSING")).isEqualTo(3);
        dailyBatch.runAll(scheduled.plusMinutes(1));
        mvc.perform(get("/api/blogs/open-blog")).andExpect(status().isNotFound());

        // 30일 뒤 내용을 지우고 주소를 비운다 (D-86)
        dailyBatch.runAll(scheduled.plusDays(31));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blogs WHERE slug = 'open-blog'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blogs WHERE deleted_at IS NOT NULL", Integer.class)).isEqualTo(1);
    }

    @Test
    void 배치는_30일_지난_삭제_글과_만료_토큰을_지운다() throws Exception {
        Long postId = writePost(member, "open-blog", "지울 글", "본문");
        mvc.perform(post("/api/posts/" + postId + "/comments").with(csrf()).cookie(owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"댓글\"}"));
        mvc.perform(delete("/api/posts/" + postId).with(csrf()).cookie(member)).andExpect(status().isNoContent());
        LocalDateTime now = LocalDateTime.now();
        dailyBatch.runAll(now.plusDays(29));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE id = ?", Integer.class, postId)).isEqualTo(1);
        dailyBatch.runAll(now.plusDays(31));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE id = ?", Integer.class, postId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments", Integer.class)).isZero();
        // 30분 무활동 로그인은 만료되어 지워진다
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens", Integer.class)).isZero();
    }

    @Test
    void 회원탈퇴는_블로그장이면_막고_멤버는_탈퇴한_회원이_된다() throws Exception {
        Long postId = writePost(member, "open-blog", "멤버 글", "본문");
        mvc.perform(post("/api/me/withdrawal").with(csrf()).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWNED_BLOGS_REMAIN"));
        mvc.perform(post("/api/me/withdrawal").with(csrf()).cookie(member).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Wrong123!\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/me/withdrawal").with(csrf()).cookie(member).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/me").cookie(member)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.authorName").value("탈퇴한 회원"));
        mvc.perform(get("/api/blogs/open-blog")).andExpect(jsonPath("$.memberCount").value(1));
        // 같은 이메일로 바로 다시 가입할 수 있다 (6.5)
        // (가입 인증번호는 1분에 한 번만 다시 받을 수 있어, 앞서 쓴 번호 기록을 지우고 다시 가입한다)
        jdbc.update("DELETE FROM verification_codes");
        signUp("member@example.com", "새멤버");

        // 30일 뒤 개인정보 삭제
        dailyBatch.runAll(LocalDateTime.now().plusDays(31));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE status = 'WITHDRAWN' AND nickname IS NULL AND name IS NULL",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void 권한을_모두_받은_부블로그장도_블로그장_전용_기능은_못_한다() throws Exception {
        // 부블로그장 지정·위임·폐쇄·공유 링크는 블로그장만 (2장 권한표, D-71, D-138, 029 US2)
        mvc.perform(put("/api/blogs/open-blog/members/" + memberId() + "/sub-owner").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subOwner\":true,\"canEditInfo\":true,\"canManageMembers\":true,\"canManagePosts\":true}"))
                .andExpect(status().isNoContent());
        addMember("open-blog", "sub@example.com", "MEMBER");
        String allPermissions = "{\"subOwner\":true,\"canEditInfo\":true,\"canManageMembers\":true,\"canManagePosts\":true}";

        // 다른 멤버를 부블로그장으로 올리거나, 자기 권한을 바꾸지 못한다
        mvc.perform(put("/api/blogs/open-blog/members/" + userId("sub@example.com") + "/sub-owner").with(csrf())
                        .cookie(member).contentType(MediaType.APPLICATION_JSON).content(allPermissions))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(put("/api/blogs/open-blog/members/" + memberId() + "/sub-owner").with(csrf())
                        .cookie(member).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subOwner\":false,\"canEditInfo\":false,\"canManageMembers\":false,\"canManagePosts\":false}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/blogs/open-blog/transfer").with(csrf()).cookie(member)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"toUserId\":" + userId("sub@example.com") + "}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/blogs/open-blog/close").with(csrf()).cookie(member))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/blogs/open-blog/share-link").with(csrf()).cookie(member))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        // 블로그장이 폐쇄를 예약한 뒤 부블로그장이 철회하지 못한다
        mvc.perform(post("/api/blogs/open-blog/close").with(csrf()).cookie(owner)).andExpect(status().isOk());
        mvc.perform(delete("/api/blogs/open-blog/close").with(csrf()).cookie(member))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(jdbc.queryForObject("SELECT role FROM blog_members WHERE user_id = ?", String.class,
                userId("sub@example.com"))).isEqualTo("MEMBER");
        assertThat(jdbc.queryForObject("SELECT role FROM blog_members WHERE user_id = ?", String.class, memberId()))
                .isEqualTo("SUB_OWNER");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_transfer_requests", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM blogs WHERE slug = 'open-blog'", String.class))
                .isEqualTo("CLOSING");
    }

    @Test
    void 공유_링크를_다시_만들면_이전_링크로는_들어갈_수_없다() throws Exception {
        // 블로그장이 링크를 새로 만들면 이전 링크는 막힌다 (BLG-01, 029 US2)
        createBlog(owner, "link-blog", "UNLISTED").andExpect(status().isCreated());
        String oldKey = jdbc.queryForObject("SELECT share_token FROM blogs WHERE slug = 'link-blog'", String.class);
        mvc.perform(get("/api/blogs/link-blog").param("key", oldKey)).andExpect(status().isOk());

        String body = mvc.perform(post("/api/blogs/link-blog/share-link").with(csrf()).cookie(owner))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newKey = jdbc.queryForObject("SELECT share_token FROM blogs WHERE slug = 'link-blog'", String.class);
        assertThat(newKey).isNotEqualTo(oldKey);
        assertThat((String) com.jayway.jsonpath.JsonPath.read(body, "$.shareUrl")).endsWith("key=" + newKey);

        mvc.perform(get("/api/blogs/link-blog").param("key", newKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("블로그 link-blog"));
        String denied = mvc.perform(get("/api/blogs/link-blog").param("key", oldKey))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();
        assertThat(denied).doesNotContain("블로그 link-blog");

        // 전체 공개 블로그에는 공유 링크가 없다
        mvc.perform(post("/api/blogs/open-blog/share-link").with(csrf()).cookie(owner))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_UNLISTED"));
    }

    @Test
    void 멤버_목록의_이메일과_전화번호는_가려서_보인다() throws Exception {
        // 4.4: 블로그장과 멤버 관리 부블로그장은 이메일·전화번호를 가려서 본다 (029 US4)
        Cookie manager = signUpAndLogin("manager@example.com", "관리멤버", "최관리", "010-7777-1234");
        addMember("open-blog", "manager@example.com", "MEMBER");
        mvc.perform(put("/api/blogs/open-blog/members/" + userId("manager@example.com") + "/sub-owner").with(csrf())
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subOwner\":true,\"canEditInfo\":false,\"canManageMembers\":true,\"canManagePosts\":false}"))
                .andExpect(status().isNoContent());

        for (Cookie viewer : new Cookie[] {owner, manager}) {
            String body = mvc.perform(get("/api/blogs/open-blog/members").cookie(viewer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.nickname == '멤버회원')].email").value(
                            org.hamcrest.Matchers.contains("me***@example.com")))
                    .andExpect(jsonPath("$[?(@.nickname == '관리멤버')].phone").value(
                            org.hamcrest.Matchers.contains("010-****-1234")))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("member@example.com").doesNotContain("manager@example.com")
                    .doesNotContain("01077771234").doesNotContain("010-7777-1234");
        }
    }

    @Test
    void 탈퇴하면_구독_대기_신청_최근_검색어를_바로_정리한다() throws Exception {
        // USR-05, 4.5 (029 US4): 개인정보 30일 보관과 별개로 이 기록들은 탈퇴 즉시 정리하고, 프로필은 바로 안 보인다
        createBlog(owner, "wait-blog", "PUBLIC", "APPROVAL").andExpect(status().isCreated());
        mvc.perform(post("/api/blogs/wait-blog/join").with(csrf()).cookie(member)).andExpect(status().isAccepted());
        mvc.perform(post("/api/blogs/wait-blog/subscription").with(csrf()).cookie(member)).andExpect(status().isOk());
        mvc.perform(get("/api/search/posts").param("q", "여행기").cookie(member)).andExpect(status().isOk());
        Long me = memberId();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recent_searches WHERE user_id = ?", Integer.class, me))
                .isEqualTo(1);
        mvc.perform(get("/api/users/멤버회원")).andExpect(status().isOk());

        mvc.perform(post("/api/me/withdrawal").with(csrf()).cookie(member).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_subscriptions WHERE user_id = ?", Integer.class, me))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recent_searches WHERE user_id = ?", Integer.class, me))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM blog_join_requests WHERE user_id = ?", String.class, me))
                .isEqualTo("CANCELED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, me)).isZero();
        mvc.perform(get("/api/users/멤버회원")).andExpect(status().isNotFound());
    }
}
