package com.oneblog.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Page;

/** 흐름 C: 권한 — 비회원 글쓰기, 남의 블로그 관리 화면 (US5, SEC-07, SEC-11). */
class AccessControlE2ETest extends E2ETestSupport {

    private static final String OWNER = "e2e-owner@example.com";
    private static final String OTHER = "e2e-other@example.com";

    @Test
    void 비회원은_글을_쓰지_못하고_로그인_화면으로_간다() throws Exception {
        apiCreateBlog(apiSignUpAndLogin(OWNER, "주인"), "owner-blog", "PUBLIC", "OPEN");

        Page anonymous = newPage(true);
        anonymous.navigate("/blog/owner-blog/write");
        assertThat(anonymous.locator("#header-user a.header-login")).isVisible();
        assertThat(anonymous.locator("#form-message")).containsText("멤버만");

        anonymous.locator("#title").fill("몰래 쓴 글");
        anonymous.locator("#content").fill("본문");
        anonymous.locator("#post-form button[type=submit]").click();
        waitForPath(anonymous, path -> path.startsWith("/login.html"));
    }

    @Test
    void 다른_회원은_남의_블로그를_관리하지_못한다() throws Exception {
        apiCreateBlog(apiSignUpAndLogin(OWNER, "주인"), "owner-blog", "PUBLIC", "OPEN");
        apiSignUp(OTHER, "손님");

        Page other = newPage(true);
        loginViaUi(other, OTHER);
        other.navigate("/blog/owner-blog/manage");
        assertThat(other.locator("#page-message")).containsText("권한이 없어요");
        assertThat(other.locator("#info-form")).isHidden();
        assertThat(other.locator("#members-block")).isHidden();
        assertThat(other.locator("#close-block")).isHidden();
    }
}
