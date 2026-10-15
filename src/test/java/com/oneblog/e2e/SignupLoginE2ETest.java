package com.oneblog.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Page;

/** 흐름 A: 가입(이메일 인증) → 로그인 → 로그아웃 (US5, USR-01~03). */
class SignupLoginE2ETest extends E2ETestSupport {

    private static final String EMAIL = "e2e-signup@example.com";
    private static final String NICKNAME = "새회원";

    @Test
    void 가입하고_로그인한_뒤_로그아웃한다() {
        Page page = newPage(true);
        page.navigate("/signup.html");

        // 1단계: 이메일 인증 (Enter는 누르지 않는다: 빈 번호로 인증 확인이 제출됨)
        page.locator("#email").fill(EMAIL);
        page.locator("#send-code").click();
        assertThat(page.locator("#code")).isVisible();
        page.locator("#code").fill(sentSignupCode(EMAIL));
        page.locator("#step-1 button[type=submit]").click();

        // 2단계: 비밀번호
        assertThat(page.locator("#step-2")).isVisible();
        page.locator("#password").fill(PASSWORD);
        page.locator("#password-confirm").fill(PASSWORD);
        page.locator("#step-2 button[type=submit]").click();

        // 3단계: 정보와 약관 동의
        assertThat(page.locator("#step-3")).isVisible();
        page.locator("#name").fill("홍길동");
        page.locator("#nickname").fill(NICKNAME);
        page.locator("#phone").fill("010-1234-5678");
        page.locator("#agree-all").check();
        assertThat(page.locator("#signup-submit")).isEnabled();
        page.locator("#signup-submit").click();

        // 가입하면 자동 로그인되지 않고 로그인 화면으로 간다
        waitForPath(page, path -> path.startsWith("/login.html?signup=done"));
        assertThat(page.locator("#signup-done")).isVisible();

        loginViaUi(page, EMAIL);
        assertThat(page.locator("#header-user a.nickname")).hasText(NICKNAME);

        logoutViaUi(page);
        assertThat(page.locator("#header-user a.nickname")).hasCount(0);
    }
}
