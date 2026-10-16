package com.oneblog.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.oneblog.post.PostTestSupport;

import jakarta.servlet.http.Cookie;

/** 메인 관리자 1차 (ADM-01~03, 05, 06, SEC-09, BRD-10, D-98 / specs/007-admin). */
class AdminIntegrationTest extends PostTestSupport {

    private Cookie admin;
    private Cookie owner;
    private Long postId;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUpAndLogin("owner@example.com", "블로그장");
        createBlog(owner, "open-blog", "PUBLIC").andExpect(status().isCreated());
        postId = writePost(owner, "open-blog", "문제 글", "본문");
        admin = createAdminAndLogin("admin01");
    }

    private ResultActions adminPost(String url, String json) throws Exception {
        return mvc.perform(post(url).with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void 관리자는_전용_아이디로만_로그인한다() throws Exception {
        mvc.perform(post("/api/auth/admin/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"admin01\",\"password\":\"Wrong123!\"}"))
                .andExpect(status().isUnauthorized());
        // 회원 계정은 관리자 로그인으로 들어올 수 없다
        mvc.perform(post("/api/auth/admin/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"owner@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(admin)).andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void 관리자는_여러_번_틀려도_잠기지_않고_짧은_비밀번호도_쓸_수_있다() throws Exception {
        for (int i = 0; i < 6; i++) {
            mvc.perform(post("/api/auth/admin/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"loginId\":\"admin01\",\"password\":\"Wrong123!\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        }
        Integer failures = jdbc.queryForObject(
                "SELECT failed_login_count FROM users WHERE login_id = 'admin01'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(failures).isZero();
        mvc.perform(post("/api/auth/admin/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"admin01\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());

        // 회원 비밀번호 규칙(SEC-02)에 안 맞는 비밀번호도 관리자는 쓸 수 있다 (D-105)
        jdbc.update("INSERT INTO users (login_id, password_hash, role, status) VALUES ('admin02', ?, 'ADMIN', 'ACTIVE')",
                passwordEncoder.encode("1234"));
        mvc.perform(post("/api/auth/admin/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"admin02\",\"password\":\"1234\"}"))
                .andExpect(status().isOk());
    }

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void 관리자는_비공개_블로그와_글을_보고_기록이_남는다() throws Exception {
        createBlog(owner, "secret-blog", "PRIVATE").andExpect(status().isCreated());
        Long secretPost = writePost(owner, "secret-blog", "비밀 글", "본문");
        mvc.perform(get("/api/blogs/secret-blog")).andExpect(status().isForbidden());
        mvc.perform(get("/api/posts/" + secretPost)).andExpect(status().isForbidden());

        mvc.perform(get("/api/blogs/secret-blog").cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.adminViewReason").value("비공개 블로그예요."));
        mvc.perform(get("/api/blogs/secret-blog/posts").cookie(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + secretPost).cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.adminViewReason").value("비공개 블로그예요."))
                .andExpect(jsonPath("$.canComment").value(false));
        // 같은 블로그를 여러 번 봐도 10분 안에는 한 번만 남는다 (ADM-06)
        Integer views = jdbc.queryForObject("SELECT COUNT(*) FROM admin_actions WHERE action_type = 'CONTENT_VIEW'"
                + " AND target_type = 'BLOG' AND target_id = ?", Integer.class, blogId("secret-blog"));
        assertThat(views).isEqualTo(1);
        // 보기만 한다: 관리자는 댓글을 쓸 수 없다
        mvc.perform(post("/api/posts/" + secretPost + "/comments").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"관리자 댓글\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 관리자_API는_관리자만() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users").cookie(owner)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/stats").cookie(owner)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").cookie(admin)).andExpect(status().isOk());
    }

    @Test
    void 회원_목록은_가려서_보이고_전체_보기는_기록이_남는다() throws Exception {
        mvc.perform(get("/api/admin/users").param("q", "블로그").cookie(admin))
                .andExpect(jsonPath("$.items[0].nickname").value("블로그장"))
                .andExpect(jsonPath("$.items[0].email").value("ow***@example.com"))
                .andExpect(jsonPath("$.items[0].phone").value("010-****-5678"))
                .andExpect(jsonPath("$.items[0].ownedBlogs").value(1));
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'owner@example.com'", Long.class);
        mvc.perform(post("/api/admin/users/" + userId + "/reveal").with(csrf()).cookie(admin))
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andExpect(jsonPath("$.phone").value("010-1234-5678"));
        assertThat(jdbc.queryForObject("SELECT action_type FROM admin_actions", String.class))
                .isEqualTo("VIEW_PERSONAL_INFO");
        mvc.perform(get("/api/admin/actions").cookie(admin))
                .andExpect(jsonPath("$.items[0].adminLoginId").value("admin01"))
                .andExpect(jsonPath("$.items[0].actionType").value("VIEW_PERSONAL_INFO"));
        // 회원 상세도 기본은 가린다. 원래 값은 기록이 남는 전체 보기(reveal)로만 (4.4, ADM-01, 029 US4)
        String detail = mvc.perform(get("/api/admin/users/" + userId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.nickname").value("블로그장"))
                .andExpect(jsonPath("$.user.email").value("ow***@example.com"))
                .andExpect(jsonPath("$.user.phone").value("010-****-5678"))
                .andExpect(jsonPath("$.blogs.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(detail).doesNotContain("owner@example.com").doesNotContain("01012345678")
                .doesNotContain("010-1234-5678");
        mvc.perform(get("/api/admin/users/" + userId).cookie(owner)).andExpect(status().isForbidden());
    }

    @Test
    void 검색어의_특수문자는_글자_그대로_찾는다() throws Exception {
        mvc.perform(get("/api/admin/users").param("q", "%").cookie(admin))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void 블로그를_숨기면_목록에서_빠지고_멤버가_아니면_못_들어간다() throws Exception {
        Long blogId = blogId("open-blog");
        adminPost("/api/admin/blogs/" + blogId + "/hide", "{\"hidden\":true,\"reason\":\"스팸\"}")
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/blogs")).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/blogs/open-blog")).andExpect(status().isNotFound());
        mvc.perform(get("/api/blogs/open-blog").cookie(owner)).andExpect(status().isOk());
        // 관리자는 숨긴 블로그도 본다 (D-106)
        mvc.perform(get("/api/blogs/open-blog").cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.adminViewReason").value("관리자가 숨긴 블로그예요."));
        adminPost("/api/admin/blogs/" + blogId + "/hide", "{\"hidden\":false}").andExpect(status().isNoContent());
        mvc.perform(get("/api/blogs/open-blog")).andExpect(status().isOk());
        // 관리자 조치는 모두 기록에 남는다 (SEC-09, ADM-06, 029 US6)
        assertThat(actions("BLOG_HIDE")).isEqualTo(1);
        assertThat(actions("BLOG_UNHIDE")).isEqualTo(1);
    }

    private int actions(String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM admin_actions WHERE action_type = ?", Integer.class, type);
    }

    @Test
    void 글을_숨기거나_지우면_보이지_않고_지운_주체가_남는다() throws Exception {
        adminPost("/api/admin/posts/" + postId + "/hide", "{\"hidden\":true}").andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + postId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/blogs/open-blog/posts")).andExpect(jsonPath("$.totalItems").value(0));
        // 관리자는 숨긴 글도 본다 (D-106)
        mvc.perform(get("/api/posts/" + postId).cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.adminViewReason").value("관리자가 숨긴 글이에요."));
        adminPost("/api/admin/posts/" + postId + "/hide", "{\"hidden\":false}").andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + postId)).andExpect(status().isOk());

        adminPost("/api/admin/posts/" + postId + "/delete", "{\"reason\":\"규정 위반\"}").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT deleted_by FROM posts WHERE id = ?", String.class, postId))
                .isEqualTo("ADMIN");
        // 관리자는 블로그 글 API로 지우지 않는다
        Long other = writePost(owner, "open-blog", "다른 글", "본문");
        mvc.perform(delete("/api/posts/" + other).with(csrf()).cookie(admin))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USE_ADMIN_API"));
        assertThat(actions("POST_HIDE")).isEqualTo(1);
        assertThat(actions("POST_UNHIDE")).isEqualTo(1);
        assertThat(actions("POST_DELETE")).isEqualTo(1);
        // 작성자에게 사유와 함께 알린다 (BRD-01)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE type = 'POST_DELETED'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void 댓글을_숨기면_목록에서_빠진다() throws Exception {
        String body = mvc.perform(post("/api/posts/" + postId + "/comments").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"숨길 댓글\"}"))
                .andReturn().getResponse().getContentAsString();
        Long commentId = ((Number) JsonPath.read(body, "$.id")).longValue();
        adminPost("/api/admin/comments/" + commentId + "/hide", "{\"hidden\":true}").andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + postId + "/comments")).andExpect(jsonPath("$.length()").value(0));
        // 관리자에게는 숨긴 댓글도 보이고 숨김 표시가 붙는다 (D-106)
        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(admin))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].hidden").value(true));
        mvc.perform(get("/api/admin/comments").cookie(admin)).andExpect(jsonPath("$.items[0].hidden").value(true))
                // 관리자 화면 "바로가기" 주소용 (/blog/{slug}/posts/{postId}#comment-{id})
                .andExpect(jsonPath("$.items[0].blogSlug").value("open-blog"))
                .andExpect(jsonPath("$.items[0].postId").value(postId.intValue()));
        assertThat(actions("COMMENT_HIDE")).isEqualTo(1);
    }

    @Test
    void 공지는_관리자가_쓰고_누구나_읽는다() throws Exception {
        String body = adminPost("/api/admin/notices", "{\"title\":\"점검 안내\",\"content\":\"**내일** 점검\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        mvc.perform(get("/api/notices")).andExpect(jsonPath("$.items[0].title").value("점검 안내"));
        mvc.perform(get("/api/notices/" + id))
                .andExpect(jsonPath("$.contentHtml").value(org.hamcrest.Matchers.containsString("<strong>내일</strong>")));
        assertThat(jdbc.queryForObject("SELECT blog_id FROM posts WHERE id = ?", Long.class, id)).isNull();
        mvc.perform(post("/api/admin/notices").with(csrf()).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"회원 공지\",\"content\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/notices/" + id).with(csrf()).cookie(admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/notices/" + id)).andExpect(status().isNotFound());
        assertThat(actions("NOTICE_CREATE")).isEqualTo(1);
        assertThat(actions("NOTICE_DELETE")).isEqualTo(1);
    }

    @Test
    void 공지는_종류가_있고_종류로_거른다() throws Exception {
        adminPost("/api/admin/notices", "{\"title\":\"0.22 업데이트\",\"content\":\"새 기능\",\"category\":\"UPDATE\"}")
                .andExpect(status().isCreated());
        adminPost("/api/admin/notices", "{\"title\":\"안내\",\"content\":\"종류 없이\"}").andExpect(status().isCreated());
        mvc.perform(get("/api/notices"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].category").value("GENERAL"))
                .andExpect(jsonPath("$.items[0].categoryLabel").value("일반"))
                .andExpect(jsonPath("$.items[1].categoryLabel").value("업데이트"));
        mvc.perform(get("/api/notices").param("category", "UPDATE"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("0.22 업데이트"));
        mvc.perform(get("/api/notices").param("category", "WRONG")).andExpect(status().isBadRequest());
        adminPost("/api/admin/notices", "{\"title\":\"x\",\"content\":\"x\",\"category\":\"WRONG\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        // 블로그 글에는 종류가 없다 (ck_posts_notice_category)
        assertThat(jdbc.queryForObject("SELECT notice_category FROM posts WHERE id = ?", String.class, postId)).isNull();
    }

    @Test
    void 관리자는_공지를_고치고_알림은_다시_보내지_않는다() throws Exception {
        String body = adminPost("/api/admin/notices", "{\"title\":\"점검 안내\",\"content\":\"내일 점검\"}")
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        long notificationsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Long.class);

        mvc.perform(get("/api/admin/notices/" + id).cookie(admin))
                .andExpect(jsonPath("$.content").value("내일 점검"))
                .andExpect(jsonPath("$.category").value("GENERAL"));
        mvc.perform(put("/api/admin/notices/" + id).with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"점검 시간 변경\",\"content\":\"**모레** 점검\",\"category\":\"MAINTENANCE\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/notices/" + id))
                .andExpect(jsonPath("$.title").value("점검 시간 변경"))
                .andExpect(jsonPath("$.categoryLabel").value("점검"))
                .andExpect(jsonPath("$.contentHtml").value(org.hamcrest.Matchers.containsString("<strong>모레</strong>")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_actions WHERE action_type = 'NOTICE_UPDATE' AND target_id = ?",
                Long.class, id)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Long.class)).isEqualTo(notificationsBefore);

        // 고친 지 1분이 넘은 것처럼 만들면 "수정됨"
        jdbc.update("UPDATE posts SET created_at = created_at - INTERVAL 2 HOUR WHERE id = ?", id);
        mvc.perform(get("/api/notices/" + id)).andExpect(jsonPath("$.edited").value(true));
    }

    @Test
    void 관리자가_아니면_공지를_고칠_수_없다() throws Exception {
        String body = adminPost("/api/admin/notices", "{\"title\":\"원래 제목\",\"content\":\"x\"}")
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        String json = "{\"title\":\"바꾼 제목\",\"content\":\"x\"}";
        mvc.perform(put("/api/admin/notices/" + id).with(csrf()).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                .content(json)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/notices/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/notices/" + id).cookie(owner)).andExpect(status().isForbidden());
        mvc.perform(get("/api/notices/" + id)).andExpect(jsonPath("$.title").value("원래 제목"));
        // 지운 공지는 고칠 수 없다
        mvc.perform(delete("/api/admin/notices/" + id).with(csrf()).cookie(admin)).andExpect(status().isNoContent());
        mvc.perform(put("/api/admin/notices/" + id).with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content(json)).andExpect(status().isNotFound());
    }

    @Test
    void 통계는_회원_블로그_글_수와_일별_가입_글_수() throws Exception {
        mvc.perform(get("/api/admin/stats").param("days", "7").cookie(admin))
                .andExpect(jsonPath("$.users").value(1))
                .andExpect(jsonPath("$.blogs").value(1))
                .andExpect(jsonPath("$.posts").value(1))
                .andExpect(jsonPath("$.daily.length()").value(7))
                .andExpect(jsonPath("$.daily[6].signups").value(1))
                .andExpect(jsonPath("$.daily[6].posts").value(1));
    }

    @Test
    void 관리자는_회원_로그인으로_들어올_수_없다() throws Exception {
        signUp("member@example.com", "곧관리자");
        makeAdmin("member@example.com", "admin02");
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 관리자는_블로그와_글을_검색해_본다() throws Exception {
        // ADM-02, ADM-03: 블로그장·멤버 수·글 수, 숨긴 글도 목록에 (029 US6)
        createBlog(owner, "quiet-blog", "PRIVATE").andExpect(status().isCreated());
        Long hiddenPost = writePost(owner, "open-blog", "숨긴 글", "본문");
        adminPost("/api/admin/posts/" + hiddenPost + "/hide", "{\"hidden\":true}").andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/blogs").param("q", "open").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("open-blog"))
                .andExpect(jsonPath("$.items[0].ownerNickname").value("블로그장"))
                .andExpect(jsonPath("$.items[0].memberCount").value(1))
                .andExpect(jsonPath("$.items[0].postCount").value(2));
        // 비공개 블로그도 관리자 목록에는 나온다
        mvc.perform(get("/api/admin/blogs").param("q", "quiet").cookie(admin))
                .andExpect(jsonPath("$.items[0].visibility").value("PRIVATE"));
        mvc.perform(get("/api/admin/posts").param("q", "숨긴").cookie(admin))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].hidden").value(true))
                .andExpect(jsonPath("$.items[0].blogSlug").value("open-blog"));
        mvc.perform(get("/api/admin/blogs").cookie(owner)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/posts").cookie(owner)).andExpect(status().isForbidden());
    }

    @Test
    void 관리자는_일부_공개_블로그와_숨긴_글을_보고_기록이_남고_지운_글은_못_본다() throws Exception {
        // ADM-02, D-106 (029 US6)
        createBlog(owner, "link-blog", "UNLISTED").andExpect(status().isCreated());
        mvc.perform(get("/api/blogs/link-blog").cookie(admin)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_actions WHERE target_type = 'BLOG' AND target_id = ?",
                Integer.class, blogId("link-blog"))).isEqualTo(1);

        adminPost("/api/admin/posts/" + postId + "/hide", "{\"hidden\":true}").andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + postId).cookie(admin)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_actions WHERE action_type = 'CONTENT_VIEW' "
                + "AND target_type = 'POST' AND target_id = ?", Integer.class, postId)).isEqualTo(1);

        mvc.perform(delete("/api/posts/" + writePost(owner, "open-blog", "지울 글", "본문")).with(csrf()).cookie(owner))
                .andExpect(status().isNoContent());
        Long deleted = jdbc.queryForObject("SELECT id FROM posts WHERE title = '지울 글'", Long.class);
        mvc.perform(get("/api/posts/" + deleted).cookie(admin)).andExpect(status().isNotFound());
    }
}
