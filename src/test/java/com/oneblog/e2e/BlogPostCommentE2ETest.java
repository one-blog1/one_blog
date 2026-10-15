package com.oneblog.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Page;

/** 흐름 B: 블로그 만들기 → 글쓰기 → 글 보기 → 댓글 (US5, BLG-01, BRD-01, BRD-06). */
class BlogPostCommentE2ETest extends E2ETestSupport {

    private static final String EMAIL = "e2e-writer@example.com";

    @Test
    void 블로그를_만들고_글과_댓글을_쓴다() throws Exception {
        signUp(EMAIL, "작가");
        Page page = newPage(true);
        loginViaUi(page, EMAIL);

        // 블로그 만들기 (기본: 공개, 자유 참여)
        page.navigate("/blog-new.html");
        page.locator("#name").fill("E2E 블로그");
        page.locator("#slug").fill("e2e-blog");
        page.locator("#blog-form button[type=submit]").click();
        waitForPath(page, path -> path.equals("/blog/e2e-blog"));
        assertThat(page.locator("#blog-name")).hasText("E2E 블로그");

        // 글쓰기
        page.locator("#write-link").click();
        waitForPath(page, path -> path.startsWith("/blog/e2e-blog/write"));
        savePostWithTextarea(page, "첫 글", "안녕하세요 E2E 본문입니다");
        assertThat(page.locator("#post-title")).hasText("첫 글");
        assertThat(page.locator("#post-body")).containsText("안녕하세요 E2E 본문입니다");

        // 댓글
        assertThat(page.locator("#comment-form")).isVisible();
        page.locator("#comment-input").fill("좋은 글이에요");
        page.locator("#comment-form button[type=submit]").click();
        assertThat(page.locator("#comment-list li.comment .comment-body")).hasText("좋은 글이에요");
        assertThat(page.locator("#comment-input")).hasValue("");
    }
}
