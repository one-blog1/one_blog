package com.oneblog.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;
import com.oneblog.post.PostTestSupport;

import jakarta.servlet.http.Cookie;

/** 알림 (SOC-04, 3.6, D-72 / specs/011-notifications). */
class NotificationIntegrationTest extends PostTestSupport {

    private Cookie owner;
    private Cookie member;
    private Long postId;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUpAndLogin("owner@example.com", "블로그장");
        member = signUpAndLogin("member@example.com", "멤버회원");
        createBlog(owner, "open-blog", "PUBLIC").andExpect(status().isCreated());
        addMember("open-blog", "member@example.com", "MEMBER");
        postId = writePost(owner, "open-blog", "블로그장 글", "본문");
    }

    private Long comment(Cookie login, String json) throws Exception {
        String body = mvc.perform(post("/api/posts/" + postId + "/comments").with(csrf()).cookie(login)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void 댓글_답글_좋아요_팔로우를_알리고_내가_한_일은_알리지_않는다() throws Exception {
        Long first = comment(member, "{\"content\":\"좋은 글이에요\"}");
        comment(owner, "{\"content\":\"고마워요\",\"parentId\":" + first + "}");
        comment(owner, "{\"content\":\"내 글에 내 댓글\"}");
        mvc.perform(post("/api/posts/" + postId + "/like").with(csrf()).cookie(member)).andExpect(status().isOk());
        mvc.perform(post("/api/users/블로그장/follow").with(csrf()).cookie(member)).andExpect(status().isOk());

        mvc.perform(get("/api/notifications").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3))
                .andExpect(jsonPath("$.items[*].type", org.hamcrest.Matchers.containsInAnyOrder("COMMENT", "LIKE", "FOLLOW")));
        mvc.perform(get("/api/notifications").param("tab", "COMMENT").cookie(member))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].type").value("REPLY"))
                .andExpect(jsonPath("$.items[0].linkUrl").value(containsString("#comment-")));

        // 좋아요를 취소했다 다시 눌러도 알림은 하나
        mvc.perform(post("/api/posts/" + postId + "/like").with(csrf()).cookie(member));
        mvc.perform(post("/api/posts/" + postId + "/like").with(csrf()).cookie(member));
        mvc.perform(get("/api/notifications").param("tab", "LIKE").cookie(owner)).andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void 읽음_처리와_전체_삭제는_내_알림만() throws Exception {
        comment(member, "{\"content\":\"댓글\"}");
        String body = mvc.perform(get("/api/notifications").cookie(owner)).andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(body, "$.items[0].id");

        mvc.perform(post("/api/notifications/" + id + "/read").with(csrf()).cookie(member))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/notifications/unread-count").cookie(owner)).andExpect(jsonPath("$.count").value(1));
        mvc.perform(post("/api/notifications/" + id + "/read").with(csrf()).cookie(owner))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread-count").cookie(owner)).andExpect(jsonPath("$.count").value(0));

        mvc.perform(delete("/api/notifications").with(csrf()).cookie(member)).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications").cookie(owner)).andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(delete("/api/notifications").with(csrf()).cookie(owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications").cookie(owner)).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void 끈_알림은_만들지_않고_꼭_필요한_알림은_끌_수_없다() throws Exception {
        mvc.perform(put("/api/me/notification-settings").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retentionDays\":7,\"settings\":{\"COMMENT\":false}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retentionDays").value(7))
                .andExpect(jsonPath("$.items[?(@.type == 'COMMENT')].enabled").value(org.hamcrest.Matchers.contains(false)));
        comment(member, "{\"content\":\"댓글\"}");
        mvc.perform(get("/api/notifications").cookie(owner)).andExpect(jsonPath("$.items", hasSize(0)));

        mvc.perform(put("/api/me/notification-settings").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"settings\":{\"POST_DELETED\":false}}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/me/notification-settings").with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"retentionDays\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 보관_기간이_지난_알림은_보이지_않는다() throws Exception {
        comment(member, "{\"content\":\"댓글\"}");
        jdbc.update("UPDATE notifications SET created_at = DATE_SUB(created_at, INTERVAL 8 DAY)");
        mvc.perform(get("/api/notifications").cookie(owner)).andExpect(jsonPath("$.items", hasSize(1)));
        jdbc.update("UPDATE users SET notification_retention_days = 7 WHERE email = 'owner@example.com'");
        mvc.perform(get("/api/notifications").cookie(owner)).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void 참여_신청과_결과_공지_글_삭제를_알린다() throws Exception {
        Cookie applicant = signUpAndLogin("applicant@example.com", "신청자");
        createBlog(owner, "approval-blog", "PUBLIC", "APPROVAL").andExpect(status().isCreated());
        mvc.perform(post("/api/blogs/approval-blog/join").with(csrf()).cookie(applicant)).andExpect(status().isAccepted());
        mvc.perform(get("/api/notifications").param("tab", "BLOG").cookie(owner))
                .andExpect(jsonPath("$.items[0].type").value("JOIN_REQUEST"));
        Long requestId = jdbc.queryForObject("SELECT id FROM blog_join_requests", Long.class);
        mvc.perform(post("/api/blogs/approval-blog/join-requests/" + requestId + "/approve").with(csrf()).cookie(owner))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/notifications").cookie(applicant))
                .andExpect(jsonPath("$.items[0].type").value("JOIN_RESULT"))
                .andExpect(jsonPath("$.items[0].message").value(containsString("승인")));

        // 블로그장이 멤버의 글을 지우면 작성자에게 알린다
        Long memberPost = writePost(member, "open-blog", "멤버 글", "본문");
        mvc.perform(delete("/api/posts/" + memberPost).with(csrf()).cookie(owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications").param("tab", "OPERATION").cookie(member))
                .andExpect(jsonPath("$.items[0].type").value("POST_DELETED"));

        // 관리자 공지는 모든 회원에게
        Cookie admin = createAdminAndLogin("admin01");
        mvc.perform(post("/api/admin/notices").with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"점검 안내\",\"content\":\"내일 점검\"}"))
                .andExpect(status().is2xxSuccessful());
        Integer notices = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE type = 'NOTICE'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(notices).isEqualTo(3);
        mvc.perform(get("/api/notifications").cookie(admin)).andExpect(status().isForbidden());
    }

    @Test
    void 관리자가_지운_댓글_알림은_사유와_지금_상태의_바로가기를_보여_준다() throws Exception {
        // ADM-03, D-107: 관리자가 댓글을 지우면 사유와 함께 알린다. 바로가기는 열 때 계산한다
        // (댓글이 지워졌으면 그 글로, 글까지 지워지면 없음). 남의 알림은 볼 수 없다 (029 US6)
        Long commentId = comment(member, "{\"content\":\"광고 댓글\"}");
        Cookie admin = createAdminAndLogin("admin01");
        mvc.perform(post("/api/admin/comments/" + commentId + "/delete").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"광고\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + postId + "/comments")).andExpect(jsonPath("$", hasSize(0)));
        Long id = jdbc.queryForObject("SELECT n.id FROM notifications n JOIN users u ON u.id = n.user_id "
                + "WHERE u.email = 'member@example.com' AND n.type = 'COMMENT_DELETED'", Long.class);

        mvc.perform(get("/api/notifications/" + id).cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detail").value(containsString("광고")))
                .andExpect(jsonPath("$.linkUrl").value(containsString("/posts/" + postId)))
                .andExpect(jsonPath("$.linkUrl").value(org.hamcrest.Matchers.not(containsString("#comment-"))));
        mvc.perform(get("/api/notifications/" + id).cookie(owner))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

        mvc.perform(delete("/api/posts/" + postId).with(csrf()).cookie(owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/" + id).cookie(member))
                .andExpect(jsonPath("$.linkUrl").doesNotExist())
                .andExpect(jsonPath("$.linkUnavailable").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_actions WHERE action_type = 'COMMENT_DELETE'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void 모두_읽음과_알림_설정_조회() throws Exception {
        // SOC-04 읽음 처리, 6.7 보관 기간 (029 US6)
        comment(member, "{\"content\":\"하나\"}");
        comment(member, "{\"content\":\"둘\"}");
        mvc.perform(get("/api/notifications/unread-count").cookie(owner)).andExpect(jsonPath("$.count").value(2));
        mvc.perform(post("/api/notifications/read-all").with(csrf()).cookie(member)).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread-count").cookie(owner)).andExpect(jsonPath("$.count").value(2));
        mvc.perform(post("/api/notifications/read-all").with(csrf()).cookie(owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread-count").cookie(owner)).andExpect(jsonPath("$.count").value(0));

        mvc.perform(get("/api/me/notification-settings").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retentionDays").value(30))
                .andExpect(jsonPath("$.items[?(@.type == 'COMMENT')].enabled").value(
                        org.hamcrest.Matchers.contains(true)));
        mvc.perform(get("/api/me/notification-settings")).andExpect(status().isUnauthorized());
    }
}
