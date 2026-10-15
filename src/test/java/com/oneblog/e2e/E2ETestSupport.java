package com.oneblog.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInfo;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.oneblog.post.PostTestSupport;

/**
 * E2E 테스트 공통 (028, D-140): 테스트 안에서 실제 서버를 무작위 포트로 띄우고, Chromium(화면 없음)이 화면을 눌러 본다.
 *
 * <ul>
 * <li>통합 테스트 준비 코드를 그대로 쓴다: 테스트마다 테스트 DB 비우기, 가짜 메일, MockMvc로 회원·블로그·글 빠르게 만들기.
 * MockMvc와 브라우저가 같은 서버·DB를 보므로, 미리 만든 회원으로 화면에서 로그인할 수 있다.</li>
 * <li>테스트마다 새 BrowserContext(쿠키·localStorage가 비어 있음). 회원 둘이 필요한 흐름은 {@link #newPage(boolean)}를 두 번 부른다.</li>
 * <li>요소는 문구가 아니라 id로 찾는다. 기다림은 Playwright 자동 대기와 assertThat을 쓴다(networkidle 금지: 알림이 30초마다 서버를 부름).</li>
 * <li>테스트가 끝나면 trace(누른 순서·화면 기록)와 마지막 화면 캡처를 build/e2e-artifacts/클래스/테스트-번호.* 로 남긴다.
 * CI는 실패했을 때 이 폴더를 결과물로 올린다. trace는 trace.playwright.dev 에 끌어다 놓으면 볼 수 있다.</li>
 * </ul>
 * 이름이 ...E2ETest로 끝나는 클래스만 ./gradlew e2eTest 에서 돈다 (test·unitTest에서는 빠짐).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public abstract class E2ETestSupport extends PostTestSupport {

    /** 사이트 전체에서 화면 요소를 기다리는 최대 시간. 느린 CI에서도 넉넉하게. */
    private static final double TIMEOUT_MS = 15_000;

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    private int port;

    private final List<BrowserContext> contexts = new ArrayList<>();
    private final List<Page> pages = new ArrayList<>();

    @BeforeAll
    static void launchBrowser() {
        // assertThat(...)의 기본 대기는 5초라 화면 대기와 같게 늘린다
        PlaywrightAssertions.setDefaultAssertionTimeout(TIMEOUT_MS);
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @AfterEach
    void saveArtifactsAndClose(TestInfo info) throws Exception {
        Path dir = Paths.get(System.getProperty("e2e.artifacts", "build/e2e-artifacts"),
                getClass().getSimpleName());
        Files.createDirectories(dir);
        String name = info.getTestMethod().map(m -> m.getName()).orElse("test");
        for (int i = 0; i < contexts.size(); i++) {
            String file = name + "-" + (i + 1);
            diag(i + 1, "끝 주소 " + pathOf(pages.get(i).url()) + ", 쿠키 "
                    + contexts.get(i).cookies().stream().map(c -> c.name).toList());
            try {
                pages.get(i).screenshot(new Page.ScreenshotOptions().setPath(dir.resolve(file + ".png")).setFullPage(true));
            } catch (RuntimeException ignored) {
                // 페이지가 이미 닫혔으면 캡처는 건너뛴다
            }
            contexts.get(i).tracing().stop(new Tracing.StopOptions().setPath(dir.resolve(file + ".zip")));
            contexts.get(i).close();
        }
        contexts.clear();
        pages.clear();
    }

    /**
     * 새 브라우저 창(쿠키가 빈 사용자 하나)을 연다.
     *
     * @param textareaEditor true면 글쓰기 에디터(Toast UI, 외부 CDN)를 막아 기본 글 입력 칸(#content)을 쓴다.
     *                       에디터 자체는 PostImageE2ETest가 확인하므로, 나머지 흐름은 바깥 사정에 흔들리지 않게 한다.
     */
    protected Page newPage(boolean textareaEditor) {
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(baseUrl())
                .setViewportSize(1280, 900)
                .setLocale("ko-KR")
                .setTimezoneId("Asia/Seoul"));
        context.setDefaultTimeout(TIMEOUT_MS);
        context.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true));
        if (textareaEditor) {
            context.route("**/uicdn.toast.com/**", Route::abort);
        }
        Page page = context.newPage();
        watch(page, contexts.size() + 1);
        contexts.add(context);
        pages.add(page);
        return page;
    }

    /**
     * 진단 기록: 서버 API의 오류 응답, 브라우저 콘솔 오류, 끊긴 요청을 "[E2E]"로 시작하는 줄로 표준 출력에 남긴다.
     * 테스트 결과 XML에 들어가고, CI의 scripts/ci-test-annotation.py가 실패한 클래스의 이 줄들을 주석으로 올린다
     * (로그 파일을 받지 않아도 원인이 보이게). 일부러 막히는 요청(예: 비회원 401)도 함께 찍힌다.
     */
    private void watch(Page page, int window) {
        page.onResponse(response -> {
            if (response.url().contains("/api/") && response.status() >= 400) {
                diag(window, response.request().method() + " " + pathOf(response.url()) + " → " + response.status()
                        + " (X-XSRF-TOKEN " + (response.request().headerValue("x-xsrf-token") == null ? "없음" : "있음") + ")");
            }
        });
        page.onConsoleMessage(message -> {
            if ("error".equals(message.type())) {
                diag(window, "콘솔 오류: " + message.text());
            }
        });
        page.onPageError(error -> diag(window, "화면 스크립트 오류: " + error));
        page.onRequestFailed(request -> {
            if (!request.url().contains("uicdn.toast.com")) {   // 텍스트 칸 모드에서 일부러 막은 에디터는 빼고
                diag(window, "요청 실패: " + request.method() + " " + request.url() + " " + request.failure());
            }
        });
    }

    private void diag(int window, String text) {
        System.out.println("[E2E] 창" + window + " " + text);
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    /** 주소가 조건을 만족할 때까지 기다린다 (예: 저장 뒤 글 화면으로 이동). */
    protected static void waitForPath(Page page, Predicate<String> path) {
        page.waitForURL(url -> path.test(pathOf(url)));
    }

    /** 전체 주소에서 경로와 쿼리만 남긴다 (http://localhost:1234/blog/a → /blog/a). */
    protected static String pathOf(String url) {
        int slash = url.indexOf('/', url.indexOf("//") + 2);
        return slash < 0 ? "/" : url.substring(slash);
    }

    /** 미리 만든 회원(MockMvc로 가입)을 화면에서 로그인시킨다. 성공 신호: 상단의 닉네임 링크. */
    protected void loginViaUi(Page page, String email) {
        loginViaUi(page, email, PASSWORD);
    }

    protected void loginViaUi(Page page, String email, String password) {
        submitLogin(page, email, password);
        waitForPath(page, path -> path.equals("/"));
        assertThat(page.locator("#header-user a.nickname")).isVisible();
    }

    /** 로그인 화면에서 이메일·비밀번호를 넣고 로그인 버튼을 누르기만 한다(실패를 확인할 때). */
    protected void submitLogin(Page page, String email, String password) {
        page.navigate("/login.html");
        page.locator("#email").fill(email);
        page.locator("#password").fill(password);
        page.locator("#login-form button[type=submit]").click();
    }

    /** 상단 프로필 사진 메뉴의 로그아웃. 끝나면 상단에 "로그인" 링크가 보인다. */
    protected void logoutViaUi(Page page) {
        page.locator("#header-user .avatar-button").click();
        page.locator("#header-user .profile-menu-list button", new Page.LocatorOptions().setHasText("로그아웃")).click();
        assertThat(page.locator("#header-user a.header-login")).isVisible();
    }

    /** 사이트 자체 확인 창(modal.js)의 확인 버튼. 브라우저 기본 confirm은 쓰지 않는다. */
    protected void confirmModal(Page page) {
        page.locator(".modal .dialog-actions button.primary").click();
    }

    /** 화면이 인증번호 받기를 누른 뒤, 가짜 메일로 보낸 번호를 읽는다. */
    protected String sentSignupCode(String email) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailService, timeout(5_000)).sendSignupCode(eq(email.toLowerCase()), code.capture());
        return code.getValue();
    }

    /** 글쓰기·고치기 화면(텍스트 칸 모드)에서 제목과 본문을 넣고 저장한다. 저장 뒤 글 화면으로 간다. */
    protected void savePostWithTextarea(Page page, String title, String content) {
        page.locator("#title").fill(title);
        assertThat(page.locator("#content")).isVisible();
        page.locator("#content").fill(content);
        page.locator("#post-form button[type=submit]").click();
        waitForPath(page, path -> path.matches("/blog/[a-z0-9-]+/posts/\\d+"));
    }
}
