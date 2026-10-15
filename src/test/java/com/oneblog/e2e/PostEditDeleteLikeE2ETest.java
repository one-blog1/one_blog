package com.oneblog.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import org.junit.jupiter.api.Test;

import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.Page;

/** 흐름 D: 좋아요 → 글 수정 → 삭제 (US5, BRD-01, BRD-06). */
class PostEditDeleteLikeE2ETest extends E2ETestSupport {

    private static final String AUTHOR = "e2e-author@example.com";
    private static final String READER = "e2e-reader@example.com";

    @Test
    void 좋아요를_누르고_글을_고친_뒤_지운다() throws Exception {
        APIRequestContext author = apiSignUpAndLogin(AUTHOR, "글쓴이");
        apiCreateBlog(author, "like-blog", "PUBLIC", "OPEN");
        long postId = apiWritePost(author, "like-blog", "고칠 글", "처음 본문");
        apiSignUp(READER, "독자");
        String postPath = "/blog/like-blog/posts/" + postId;

        // 다른 회원의 좋아요: 누르면 1, 다시 누르면 0
        Page reader = newPage(true);
        loginViaUi(reader, READER);
        reader.navigate(postPath);
        assertThat(reader.locator("#like-button .like-count")).hasText("0");
        reader.locator("#like-button").click();
        assertThat(reader.locator("#like-button")).hasAttribute("aria-pressed", "true");
        assertThat(reader.locator("#like-button .like-count")).hasText("1");
        reader.locator("#like-button").click();
        assertThat(reader.locator("#like-button")).hasAttribute("aria-pressed", "false");
        assertThat(reader.locator("#like-button .like-count")).hasText("0");
        // 남의 글에는 고치기·지우기가 없다
        assertThat(reader.locator("#edit-link")).isHidden();

        // 글쓴이: 고치기
        Page page = newPage(true);
        loginViaUi(page, AUTHOR);
        page.navigate(postPath);
        page.locator("#edit-link").click();
        waitForPath(page, path -> path.equals(postPath + "/edit"));
        assertThat(page.locator("#title")).hasValue("고칠 글");
        savePostWithTextarea(page, "고친 글", "고친 본문");
        assertThat(page.locator("#post-title")).hasText("고친 글");
        assertThat(page.locator("#post-body")).containsText("고친 본문");

        // 지우기: 사이트 확인 창 → 블로그 화면, 목록에 없음
        page.locator("#delete-button").click();
        confirmModal(page);
        waitForPath(page, path -> path.equals("/blog/like-blog"));
        assertThat(page.locator("#post-empty")).isVisible();
        assertThat(page.locator("#post-list")).not().containsText("고친 글");
    }
}
