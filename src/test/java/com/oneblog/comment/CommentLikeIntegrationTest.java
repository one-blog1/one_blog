package com.oneblog.comment;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.oneblog.post.PostTestSupport;

import jakarta.servlet.http.Cookie;

/** 댓글·대댓글·좋아요 (BRD-06, 6.6, D-78, D-87 / specs/006-comments-likes). */
class CommentLikeIntegrationTest extends PostTestSupport {

    private Cookie owner;
    private Cookie alice;
    private Cookie bob;
    private Long postId;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUpAndLogin("owner@example.com", "블로그장");
        alice = signUpAndLogin("alice@example.com", "앨리스");
        bob = signUpAndLogin("bob@example.com", "밥아저씨");
        createBlog(owner, "talk-blog", "PUBLIC").andExpect(status().isCreated());
        postId = writePost(owner, "talk-blog", "이야기", "본문");
    }

    private ResultActions comment(Cookie who, String content, Long parentId) throws Exception {
        String body = parentId == null ? "{\"content\":\"" + content + "\"}"
                : "{\"content\":\"" + content + "\",\"parentId\":" + parentId + "}";
        return mvc.perform(post("/api/posts/" + postId + "/comments").with(csrf()).cookie(who)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Long commentId(Cookie who, String content, Long parentId) throws Exception {
        String body = comment(who, content, parentId).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private int commentCount() {
        return jdbc.queryForObject("SELECT comment_count FROM posts WHERE id = ?", Integer.class, postId);
    }

    @Test
    void 공개_글에는_로그인한_누구나_댓글을_쓴다() throws Exception {
        commentId(alice, "좋은 글이에요", null);
        assertThat(commentCount()).isEqualTo(1);
        mvc.perform(get("/api/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].authorName").value("앨리스"))
                .andExpect(jsonPath("$[0].content").value("좋은 글이에요"))
                .andExpect(jsonPath("$[0].canEdit").value(false));
        mvc.perform(post("/api/posts/" + postId + "/comments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"비회원\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 댓글은_1에서_500자() throws Exception {
        comment(alice, "   ", null).andExpect(status().isBadRequest());
        comment(alice, "가".repeat(501), null).andExpect(status().isBadRequest());
        comment(alice, "가".repeat(500), null).andExpect(status().isCreated());
    }

    @Test
    void 대댓글은_1단계이고_답한_상대를_남긴다() throws Exception {
        Long root = commentId(alice, "첫 댓글", null);
        Long reply = commentId(bob, "답글", root);
        // 대댓글에 다시 답해도 같은 첫 댓글 아래에, 상대는 밥
        Long replyToReply = commentId(alice, "답글의 답글", reply);
        assertThat(jdbc.queryForObject("SELECT parent_id FROM comments WHERE id = ?", Long.class, replyToReply))
                .isEqualTo(root);
        mvc.perform(get("/api/posts/" + postId + "/comments"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].replies.length()").value(2))
                .andExpect(jsonPath("$[0].replies[0].replyToNickname").value("앨리스"))
                .andExpect(jsonPath("$[0].replies[1].replyToNickname").value("밥아저씨"));
    }

    @Test
    void 답글이_있는_댓글을_지우면_삭제된_댓글로_남는다() throws Exception {
        Long root = commentId(alice, "지울 댓글", null);
        commentId(bob, "남을 답글", root);
        mvc.perform(delete("/api/comments/" + root).with(csrf()).cookie(alice)).andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + postId + "/comments"))
                .andExpect(jsonPath("$[0].deleted").value(true))
                .andExpect(jsonPath("$[0].content").doesNotExist())
                .andExpect(jsonPath("$[0].authorName").doesNotExist())
                .andExpect(jsonPath("$[0].replies[0].content").value("남을 답글"));

        Long lonely = commentId(alice, "답글 없는 댓글", null);
        mvc.perform(delete("/api/comments/" + lonely).with(csrf()).cookie(alice)).andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + postId + "/comments")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void 댓글은_본인만_고치고_수정됨이_보인다() throws Exception {
        Long id = commentId(alice, "원래", null);
        mvc.perform(put("/api/comments/" + id).with(csrf()).cookie(bob).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"남이 고침\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/comments/" + id).with(csrf()).cookie(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"고침\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + postId + "/comments"))
                .andExpect(jsonPath("$[0].content").value("고침"))
                .andExpect(jsonPath("$[0].edited").value(true));
    }

    @Test
    void 블로그장은_남의_댓글을_지울_수_있고_다른_회원은_못_한다() throws Exception {
        Long id = commentId(alice, "문제 댓글", null);
        mvc.perform(delete("/api/comments/" + id).with(csrf()).cookie(bob)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/comments/" + id).with(csrf()).cookie(owner)).andExpect(status().isNoContent());
        assertThat(commentCount()).isZero();
    }

    @Test
    void 다른_글의_댓글에는_답글을_달_수_없다() throws Exception {
        Long other = writePost(owner, "talk-blog", "다른 글", "본문");
        String body = mvc.perform(post("/api/posts/" + other + "/comments").with(csrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"저쪽 댓글\"}"))
                .andReturn().getResponse().getContentAsString();
        Long otherComment = ((Number) JsonPath.read(body, "$.id")).longValue();
        comment(bob, "엉뚱한 답글", otherComment).andExpect(status().isNotFound());
    }

    @Test
    void 비공개_블로그의_글에는_멤버가_아니면_댓글을_볼_수도_쓸_수도_없다() throws Exception {
        createBlog(owner, "secret-blog", "PRIVATE").andExpect(status().isCreated());
        Long secret = writePost(owner, "secret-blog", "비밀", "본문");
        mvc.perform(get("/api/posts/" + secret + "/comments").cookie(alice)).andExpect(status().isForbidden());
        mvc.perform(post("/api/posts/" + secret + "/comments").with(csrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"몰래\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 좋아요는_한_번만이고_다시_누르면_취소된다() throws Exception {
        mvc.perform(post("/api/posts/" + postId + "/like").with(csrf()).cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));
        mvc.perform(get("/api/posts/" + postId).cookie(alice)).andExpect(jsonPath("$.liked").value(true));
        mvc.perform(post("/api/posts/" + postId + "/like").with(csrf()).cookie(bob))
                .andExpect(jsonPath("$.likeCount").value(2));
        mvc.perform(post("/api/posts/" + postId + "/like").with(csrf()).cookie(alice))
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_likes", Integer.class)).isEqualTo(1);
        mvc.perform(post("/api/posts/" + postId + "/like").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void 관리자는_댓글과_좋아요를_하지_않는다() throws Exception {
        makeAdmin("bob@example.com", "admin01");
        comment(bob, "관리자 댓글", null).andExpect(status().isForbidden());
        mvc.perform(post("/api/posts/" + postId + "/like").with(csrf()).cookie(bob)).andExpect(status().isForbidden());
    }
}
