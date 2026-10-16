package com.oneblog.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * 글 공유 창 (031, BRD-07, 6.2): 위에 주소 칸·복사, 아래에 X·텔레그램·페이스북·기기 공유 아이콘.
 * 보이는 아이콘은 블로그 공개 범위(서버의 shareScope)로 정한다: 공개는 모두, 일부 공개는 기기 공유만, 비공개는 없음.
 */
class PostShareE2ETest extends E2ETestSupport {

    private static final String OWNER = "e2e-share@example.com";
    private static final String NICKNAME = "공유하는사람";
    private static final String BODY = "본문";
    private static final String OPEN = "OPEN";
    private static final String SHARE_BUTTON = "#share-button";
    private static final String MODAL = ".modal";
    private static final String DEVICE = "device";
    private static final String HREF = "href";
    /** 기기 공유 창(navigator.share)이 없는 브라우저(CI의 리눅스 Chromium)에서 가짜를 넣고, 넘어온 값을 기록한다. */
    private static final String FAKE_DEVICE_SHARE = """
            Object.defineProperty(navigator, 'share', { configurable: true,
              value: async (data) => { window.__shared = data; } });
            """;

    @Test
    void 공개_글은_주소_복사와_SNS_아이콘을_보여_준다() {
        APIRequestContext owner = apiSignUpAndLogin(OWNER, NICKNAME);
        apiCreateBlog(owner, "share-open", "PUBLIC", OPEN);
        long postId = apiWritePost(owner, "share-open", "공유할 글", BODY);

        Page page = newPage(true);
        page.navigate("/blog/share-open/posts/" + postId);
        String url = page.url();
        String encoded = URLEncoder.encode(url, StandardCharsets.UTF_8);

        page.locator(SHARE_BUTTON).click();
        assertThat(item(page, "url")).hasValue(url);
        assertThat(item(page, "x")).hasAttribute(HREF,
                Pattern.compile("^https://x\\.com/intent/tweet\\?text=.+&url=" + Pattern.quote(encoded) + "$"));
        assertThat(item(page, "telegram")).hasAttribute(HREF,
                Pattern.compile("^https://t\\.me/share/url\\?url=" + Pattern.quote(encoded) + "&text=.+$"));
        assertThat(item(page, "facebook")).hasAttribute(HREF,
                "https://www.facebook.com/sharer/sharer.php?u=" + encoded);
        for (String key : List.of("x", "telegram", "facebook")) {
            assertThat(item(page, key)).hasAttribute("target", "_blank");
            assertThat(item(page, key)).hasAttribute("rel", "noopener noreferrer");
        }
        // 기기 공유 창이 없는 브라우저에서는 보이지 않는다
        assertThat(item(page, DEVICE)).hasCount(0);

        // Esc로 닫고, 다시 열어 복사 (지금까지의 링크 복사와 같은 안내)
        page.keyboard().press("Escape");
        assertThat(page.locator(MODAL)).hasCount(0);
        BrowserContext context = page.context();
        context.grantPermissions(List.of("clipboard-read", "clipboard-write"));
        page.locator(SHARE_BUTTON).click();
        item(page, "copy").click();
        assertThat(page.locator(MODAL)).hasCount(0);
        assertThat(page.locator("#post-message"))
                .hasText(Pattern.compile("링크를 복사했어요\\.|" + Pattern.quote(url)));
    }

    @Test
    void 기기_공유는_제목과_주소를_넘긴다() {
        APIRequestContext owner = apiSignUpAndLogin(OWNER, NICKNAME);
        apiCreateBlog(owner, "share-device", "PUBLIC", OPEN);
        long postId = apiWritePost(owner, "share-device", "기기로 보낼 글", BODY);

        Page page = newPage(true);
        page.addInitScript(FAKE_DEVICE_SHARE);
        page.navigate("/blog/share-device/posts/" + postId);
        String url = page.url();

        page.locator(SHARE_BUTTON).click();
        item(page, DEVICE).click();
        assertThat(page.locator(MODAL)).hasCount(0);
        org.assertj.core.api.Assertions.assertThat(page.evaluate("window.__shared && window.__shared.title"))
                .isEqualTo("기기로 보낼 글");
        org.assertj.core.api.Assertions.assertThat(page.evaluate("window.__shared && window.__shared.url"))
                .isEqualTo(url);
    }

    @Test
    void 일부_공개와_비공개_글은_SNS_아이콘을_숨긴다() {
        APIRequestContext owner = apiSignUpAndLogin(OWNER, NICKNAME);
        apiCreateBlog(owner, "share-link", "UNLISTED", OPEN);
        long linked = apiWritePost(owner, "share-link", "링크로만 볼 글", BODY);
        apiCreateBlog(owner, "share-secret", "PRIVATE", OPEN);
        long secret = apiWritePost(owner, "share-secret", "멤버만 볼 글", BODY);
        String key = jdbc.queryForObject("SELECT share_token FROM blogs WHERE slug = 'share-link'", String.class);

        // 일부 공개: 링크를 받은 비회원. 주소 칸에는 key가 그대로, SNS는 없고 기기 공유만
        Page guest = newPage(true);
        guest.addInitScript(FAKE_DEVICE_SHARE);
        guest.navigate("/blog/share-link/posts/" + linked + "?key=" + key);
        guest.locator(SHARE_BUTTON).click();
        assertThat(item(guest, "url")).hasValue(Pattern.compile("\\?key=" + Pattern.quote(key) + "$"));
        assertThat(item(guest, DEVICE)).isVisible();
        assertThat(guest.locator(".modal [data-share=x], .modal [data-share=telegram], .modal [data-share=facebook]"))
                .hasCount(0);
        assertThat(guest.locator(".modal .share-note")).isVisible();

        // 비공개: 블로그장. 주소 칸·복사만
        Page member = newPage(true);
        member.addInitScript(FAKE_DEVICE_SHARE);
        loginViaUi(member, OWNER);
        member.navigate("/blog/share-secret/posts/" + secret);
        member.locator(SHARE_BUTTON).click();
        assertThat(item(member, "copy")).isVisible();
        assertThat(member.locator(".modal .share-item")).hasCount(0);
        assertThat(member.locator(".modal .share-note")).isVisible();
    }

    /** 공유 창 안의 요소 (contracts/share-ui.md의 data-share 값). */
    private static Locator item(Page page, String key) {
        return page.locator(".modal [data-share=" + key + "]");
    }
}
