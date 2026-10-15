package com.oneblog.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.oneblog.file.TestImages;

/**
 * 흐름 F: 진짜 글쓰기 에디터(Toast UI 3.2.2, 외부 CDN)로 사진을 올려 글에 넣는다 (US5, BRD-01, 6.3).
 * 다른 흐름은 에디터를 막고 텍스트 칸을 쓰므로, 에디터가 실제로 동작하는지는 이 테스트가 맡는다.
 * 선택자는 Toast UI 3.2.2 원본에서 확인: 툴바 button.image, 파일 입력 #toastuiImageFileInput(기본 탭 file), 확인 .toastui-editor-ok-button
 */
class PostImageE2ETest extends E2ETestSupport {

    private static final String EMAIL = "e2e-photo@example.com";

    @TempDir
    Path tempDir;

    @Test
    void 에디터로_사진을_올려_글에_넣는다() throws Exception {
        apiCreateBlog(apiSignUpAndLogin(EMAIL, "사진가"), "photo-blog", "PUBLIC", "OPEN");
        Path photo = tempDir.resolve("photo.png");
        Files.write(photo, TestImages.png());

        Page page = newPage(false);
        loginViaUi(page, EMAIL);
        page.navigate("/blog/photo-blog/write");
        // 에디터가 CDN에서 뜨기를 기다린다. 여기서 실패하면 CDN(uicdn.toast.com) 문제이니 다시 실행해 본다
        Locator markdown = page.locator("#editor .toastui-editor-md-container .ProseMirror");
        assertThat(markdown).isVisible();

        page.locator("#title").fill("사진 글");
        page.locator("#editor button.image").click();
        page.locator("#toastuiImageFileInput").setInputFiles(photo);
        page.locator(".toastui-editor-ok-button").click();
        // 서버에 올라간 사진 주소가 본문(마크다운)에 들어간다
        assertThat(markdown).containsText("/files/");

        page.locator("#post-form button[type=submit]").click();
        waitForPath(page, path -> path.matches("/blog/photo-blog/posts/\\d+"));
        Locator image = page.locator("#post-body img[src^='/files/']");
        assertThat(image).isVisible();

        String src = image.getAttribute("src");
        assertThat(page.request().get(src).status()).isEqualTo(200);
    }
}
