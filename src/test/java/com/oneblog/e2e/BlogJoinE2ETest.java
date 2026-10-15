package com.oneblog.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/** 흐름 E: 승인제 블로그에 참여 신청 → 블로그장 승인 → 멤버가 글쓰기 (US5, BLG-04~06, SEC-07). */
class BlogJoinE2ETest extends E2ETestSupport {

    private static final String OWNER = "e2e-host@example.com";
    private static final String APPLICANT = "e2e-guest@example.com";

    @Test
    void 참여_신청이_승인되면_글을_쓸_수_있다() throws Exception {
        apiCreateBlog(apiSignUpAndLogin(OWNER, "블로그장"), "join-blog", "PUBLIC", "APPROVAL");
        apiSignUp(APPLICANT, "신청자");

        // 신청자: 참여 신청 (승인 전에는 글쓰기 링크가 없다)
        Page applicant = newPage(true);
        loginViaUi(applicant, APPLICANT);
        applicant.navigate("/blog/join-blog");
        assertThat(applicant.locator("#join-button")).containsText("참여 신청");
        assertThat(applicant.locator("#write-link")).isHidden();
        applicant.locator("#join-button").click();
        assertThat(applicant.locator("#join-state")).containsText("참여 신청 중");

        // 블로그장: 블로그 화면의 참여 신청 목록에서 승인
        Page host = newPage(true);
        loginViaUi(host, OWNER);
        host.navigate("/blog/join-blog");
        Locator request = host.locator("#request-list li", new Page.LocatorOptions().setHasText("신청자"));
        request.locator("button", new Locator.LocatorOptions().setHasText("승인")).click();
        assertThat(host.locator("#request-empty")).isVisible();

        // 신청자: 멤버가 되어 글을 쓴다
        applicant.reload();
        assertThat(applicant.locator("#blog-meta")).containsText("나: 멤버");
        applicant.locator("#write-link").click();
        waitForPath(applicant, path -> path.startsWith("/blog/join-blog/write"));
        savePostWithTextarea(applicant, "멤버의 첫 글", "승인받고 씁니다");
        assertThat(applicant.locator("#post-title")).hasText("멤버의 첫 글");
    }
}
