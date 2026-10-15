package com.oneblog.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Page;

/**
 * 흐름 G: 로그인 실패 안내 → 비밀번호 변경 → 탈퇴 (US5, USR-03·USR-04·USR-05).
 * 블로그장은 탈퇴할 수 없으므로 블로그 없는 회원으로 한다. 로그인 실패는 5번이면 잠기므로 적게 틀린다.
 */
class AccountE2ETest extends E2ETestSupport {

    private static final String EMAIL = "e2e-account@example.com";
    private static final String NEW_PASSWORD = "Newpw456!";

    @Test
    void 비밀번호를_바꾸고_탈퇴한다() throws Exception {
        apiSignUp(EMAIL, "계정주인");
        Page page = newPage(true);

        // 틀린 비밀번호: 안내가 보이고 로그인 화면에 머문다
        submitLogin(page, EMAIL, "Wrong123!");
        assertThat(page.locator("#login-message")).containsText("올바르지 않습니다");
        assertThat(page.locator("#header-user a.nickname")).hasCount(0);

        // 비밀번호 바꾸기: 내 정보 → 비밀번호 다시 확인 → 새 비밀번호
        loginViaUi(page, EMAIL);
        page.navigate("/account.html");
        page.locator("#edit-start").click();
        assertThat(page.locator("#reauth-view")).isVisible();
        page.locator("#reauth-password").fill(PASSWORD);
        page.locator("#reauth-form button[type=submit]").click();
        assertThat(page.locator("#edit-view")).isVisible();
        page.locator("#new-password").fill(NEW_PASSWORD);
        page.locator("#new-password-confirm").fill(NEW_PASSWORD);
        page.locator("#password-form button[type=submit]").click();
        assertThat(page.locator("#password-message")).containsText("비밀번호를 바꿨어요");

        // 옛 비밀번호는 안 되고 새 비밀번호는 된다
        logoutViaUi(page);
        submitLogin(page, EMAIL, PASSWORD);
        assertThat(page.locator("#login-message")).containsText("올바르지 않습니다");
        loginViaUi(page, EMAIL, NEW_PASSWORD);

        // 탈퇴: 동의 + 비밀번호 → 첫 화면, 비로그인
        page.navigate("/withdraw.html");
        page.locator("#withdraw-agree").check();
        page.locator("#withdraw-password").fill(NEW_PASSWORD);
        assertThat(page.locator("#withdraw-submit")).isEnabled();
        page.locator("#withdraw-submit").click();
        waitForPath(page, path -> path.equals("/"));
        assertThat(page.locator("#header-user a.header-login")).isVisible();

        // 탈퇴한 계정으로는 로그인되지 않는다
        submitLogin(page, EMAIL, NEW_PASSWORD);
        assertThat(page.locator("#login-message")).containsText("올바르지 않습니다");
    }
}
