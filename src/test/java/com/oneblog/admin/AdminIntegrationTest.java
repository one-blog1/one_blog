package com.oneblog.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

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

    private org.springframework.test.web.servlet.ResultActions adminPost(String url, String json) throws Exception {
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

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

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
        mvc.perform(delete("/api/posts/" + other).with(csrf()).cookie(admin)).andExpect(status().isForbidden());
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
}
