# Implementation Plan: 배포 단계별 테스트 (CI 단계 나누기 + 정적 분석)

**Branch**: `feature/ci-test-stages` | **Date**: 2026-10-15 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/028-ci-test-stages/spec.md` (2026-10-15 확인)

## Summary

CI 한 덩어리(매번 MySQL + 전체 테스트 + jar + Docker)를 둘로 나눈다. **1단계 `unit`**은 기능 브랜치 push와 PR에서 DB 없이 단위 테스트와 커버리지만 돌려 빨리 끝내고, PR merge의 필수 검사가 된다. **2단계 `full`**은 `develop`·`main`에 merge됐을 때 지금 CI 전체에 커버리지와 SonarQube 분석(품질 게이트 대기)을 더해 돈다. 게이트 실패는 CI 실패라 `main`이면 배포되지 않는다. 운영 배포에는 프록시 전환 직후 스모크와 실패 시 자동 되돌리기를 더한다.

**E2E (US5, 2026-10-15 추가)**: `full`에서 전체 테스트 다음에 Playwright(Java)로 핵심 흐름 7개를 실제 브라우저로 돌린다. 서버는 테스트가 직접 띄우고(무작위 포트), DB는 CI의 테스트 DB를 쓴다.

앱 코드, DB, ERD, 화면은 바꾸지 않는다. 바뀌는 것은 `build.gradle.kts`, `.github/workflows/ci.yml`, `.github/workflows/deploy.yml`(업로드 목록 한 줄), `scripts/deploy.sh`, 문서(readme·DEPLOY.md, 문서 저장소 7장·8장 D-140)다.

## Technical Context

**Language/Version**: Java 21, Gradle 8.14.3(wrapper), Spring Boot 4.1.1. CI는 GitHub Actions(ubuntu-latest), 배포 스크립트는 bash

**Primary Dependencies**: 새로 더함 — Gradle `jacoco`(내장), `org.sonarqube` 7.5.0.8588(구현 때 최신 확인). 나머지 그대로

**Storage**: 해당 없음 (DB 변경 없음 → data-model.md 없음)

**Testing**: JUnit 5. 단위 13개 클래스 / 통합 32개 클래스(`*IntegrationTest`, MySQL 8.4). 이 작업 환경은 Gradle·Maven 저장소가 막혀 있어 빌드 확인은 브랜치 push 후 CI로 한다(027과 같음)

**Target Platform**: GitHub Actions, 운영 서버(블루-그린, 프록시 8430, 앱 8431~8434), SonarQube Community `http://s4.java21.net:9000`

**Project Type**: 웹 서비스의 빌드·배포 설정

**Performance Goals**: 1단계 5분 안 (SC-001). 2단계는 지금 시간 + 분석(수십 초~수 분)

**Constraints**: 서버가 Community(브랜치 하나, PR 분석 없음) → 분석은 `develop`·`main` push만. 서버가 http → 토큰은 이 프로젝트 분석 전용. workflow 이름 `CI`와 배포의 `workflow_run` 조건은 바꾸지 않음

**Scale/Scope**: 파일 5개 + 문서

## Constitution Check

| 원칙 | 확인 |
|---|---|
| I. 요구사항 기준 | D-117·D-118·D-120·D-135·D-136·D-137 인용. 새 결정 D-140(단계별 CI, SonarQube 서버, 게이트는 merge 뒤)을 8장에, 7장 구현 표에 "CI 단계·정적 분석" 줄을 같은 작업에서 더함(문서 저장소는 main에 바로) |
| II. 작은 단위 완성 | 1단계·2단계·운영 스모크가 각각 따로 동작·확인 가능. PR 분석·E2E·부하는 "후속 작업" |
| III. 보안 기준선 | 토큰·서버 주소는 GitHub Secrets만, 저장소·로그에 없음(Actions가 가림). http라 프로젝트 전용 분석 토큰 사용(spec Assumptions). 운영 스모크는 읽기 요청만 |
| IV. 구조 | DB·Flyway·서버 상태 변경 없음 |
| V. 단순함 | workflow 하나에 job 둘, 이름 규칙으로 테스트 분리(파일 이동·태그 없음), 기존 `smoke-check.sh` 재사용. 스테이징·카나리 없음(Q1·Q2) |

통과. 위반 없음.

## Project Structure

### Documentation (this feature)

```text
specs/028-ci-test-stages/
├── spec.md
├── plan.md            # 이 파일
├── research.md        # R1~R7 결정과 이유
├── quickstart.md      # 확인 방법
├── contracts/
│   └── ci-stages.md   # 언제 무엇이 도는지, 밖에서 기대하는 이름
└── checklists/requirements.md
```

### Source Code (바꿀 파일)

```text
build.gradle.kts                 # jacoco, org.sonarqube 플러그인 / unitTest·e2eTest·playwrightInstall 작업 / 커버리지 보고서 2개 / sonar 블록 / playwright 의존성
src/test/java/com/oneblog/e2e/   # E2ETestSupport + 흐름별 E2E 테스트 7개 (새 폴더)
.github/workflows/ci.yml         # job unit(1단계) + full(2단계), full에 커버리지·sonar 단계, fetch-depth 0
.github/workflows/deploy.yml     # 서버 업로드 목록에 scripts/smoke-check.sh 추가
scripts/deploy.sh                # 5) 전환 직후 운영 스모크 → 실패 시 upstream 되돌리기
readme.md                        # 테스트 이름 규칙, 단계 표, SonarQube 보는 법, 브랜치 보호 켜는 법
DEPLOY.md                        # 배포 흐름에 운영 스모크·되돌리기, CI 단계 표
```

문서 저장소(one-blog_docs, main에 바로): `docs/requirements/07-implementation.md`(CI 줄), `08-decision-log.md`(D-140), 기능을 마치면 `specs/028-ci-test-stages/` 이동.

**Structure Decision**: 앱 소스(`src/`)는 손대지 않는다. 테스트 분리는 이름 규칙만 쓴다(research R1).

## 설계 요점

### build.gradle.kts

- `plugins { jacoco; id("org.sonarqube") version "..." }`
- `val unitTest by tasks.registering(Test::class)`: `test`와 같은 클래스 경로·설정(시간대, 프로필, 로그), `exclude("**/*IntegrationTest*")`. 기존 `tasks.withType<Test>` 설정이 그대로 적용된다
- `jacocoUnitTestReport`(`unitTest` 실행 결과), `jacocoTestReport`(`test`): XML·HTML
- `sonar { properties { projectKey=one-blog, projectName=One Blog, projectVersion=version, qualitygate.wait=true, qualitygate.timeout=300, coverage.jacoco.xmlReportPaths=jacocoTestReport XML } }`. 주소·토큰은 넣지 않음(환경변수)
- `tasks.named("sonar") { dependsOn("jacocoTestReport") }`는 넣지 않는다: CI에서 순서를 명시하고, 로컬에서 실수로 전체 테스트가 돌지 않게

### ci.yml

- `on`은 지금 그대로(push 4종 + pull_request + workflow_dispatch)
- `unit.if`: `github.event_name == 'pull_request' || (github.event_name == 'push' && (startsWith(github.ref, 'refs/heads/feature/') || startsWith(github.ref, 'refs/heads/hotfix/')))`
- `full.if`: `github.event_name == 'workflow_dispatch' || (github.event_name == 'push' && (github.ref == 'refs/heads/develop' || github.ref == 'refs/heads/main'))`
- `full`의 SonarQube 단계 `if`: `github.event_name == 'push'`, `env: SONAR_TOKEN, SONAR_HOST_URL`. 동시 분석은 따로 막지 않는다(research R4)
- 실패 표시 스크립트(테스트 결과 XML → Annotations)는 두 job에 같이 쓴다

### deploy.sh

- 5) 전환·`health` 확인 뒤 `WAIT_SECONDS=30 bash "$DIR/smoke-check.sh" "http://127.0.0.1:$PROXY_PORT"`
- 실패 시 `rollback_proxy`: `ACTIVE_COLOR`가 있으면 그 색 포트로 `write_upstream` → `nginx -t` → `reload` → 새 컨테이너 `stop` + `-failed`로 이름 바꿈 → `fail "운영 확인 실패, 옛 버전(...)으로 되돌렸습니다"`. 없으면(첫 배포) `fail`만

## Complexity Tracking

없음.

## E2E 설계 (US5)

### Gradle
- `testImplementation("com.microsoft.playwright:playwright:1.63.0")`
- `test`·`unitTest`는 `**/*E2ETest*` 제외. `e2eTest`(Test 작업)는 `**/*E2ETest*`만
- `playwrightInstall`(JavaExec, `com.microsoft.playwright.CLI install --with-deps chromium`): CI에서 E2E 전에. 로컬은 처음 한 번
- `jacocoTestReport`는 `build/jacoco/`의 `test.exec`와(있으면) `e2eTest.exec`를 합쳐 SonarQube에 넘긴다

### 테스트 코드 (`src/test/java/com/oneblog/e2e/`)
- `E2ETestSupport extends PostTestSupport`, `@SpringBootTest(webEnvironment = RANDOM_PORT)`
  - 상속받는 것: 테스트 DB 비우기, 가짜 `MailService`, `signUp`·`signUpAndLogin`·`createBlog(..., joinPolicy)`·`writePost` 같은 빠른 준비(MockMvc로 같은 서버·DB에 만듦)
  - 브라우저: 클래스마다 Chromium 하나(화면 없이), 테스트마다 새 BrowserContext(쿠키·localStorage 분리 → 글쓰기 임시 저장 창이 안 뜸). 화면 1280×900, 기본 대기 15초
  - 도우미: `open(path)`, `loginViaUi(page, email)`(성공 신호 `#header-user a.nickname`), `logoutViaUi(page)`(`.avatar-button` → 메뉴 "로그아웃"), `confirmModal(page)`(`.modal .dialog-actions button.primary`, 이 사이트는 브라우저 기본 confirm을 쓰지 않음), `useTextareaEditor(context)`(에디터 CDN을 막아 `#content` 텍스트 칸으로 씀)
  - 인증번호: 화면이 `#send-code`를 누른 뒤 `verify(mailService, timeout(5000)).sendSignupCode(email, captor)`로 읽음
  - 기록: 테스트마다 trace를 켜고 끝나면 `build/e2e-artifacts/<클래스>/<테스트>.zip`과 마지막 화면 캡처를 남김. CI는 실패했을 때만 올림
  - `networkidle` 대기는 쓰지 않는다(알림 30초 폴링·임시 저장 타이머). 요소·주소로 기다린다
- 흐름별 클래스와 확인할 것 (조사 결과 기준)

| 클래스 | 흐름 | 준비(빠른 길) | 브라우저로 하는 일과 확인 |
|---|---|---|---|
| `SignupLoginE2ETest` | A | 없음 | `/signup.html` 3단계(`#email`→`#send-code`→`#code`→인증하기→`#password`·`#password-confirm`→다음→`#name`·`#nickname`·`#phone`·`#agree-all`→`#signup-submit`) → `/login.html?signup=done`의 `#signup-done` → 로그인 → `a.nickname` → 로그아웃 → `a.header-login` |
| `BlogPostCommentE2ETest` | B | 회원 1 | 로그인 → `/blog-new.html`(`#name`·`#slug`, 기본 공개·자유 참여) → `/blog/{slug}` → `#write-link` → `#title`·`#content`(텍스트 칸) 저장 → `/blog/{slug}/posts/{id}`의 `#post-title`·`#post-body` → `#comment-input` 쓰기 → `#comment-list` |
| `AccessControlE2ETest` | C | 회원 1·2, 회원 1의 공개 블로그 | 비로그인 `/blog/{slug}/write`: `#form-message` "멤버만" → 저장 누르면 `/login.html`. 회원 2 `/blog/{slug}/manage`: `#page-message` "권한이 없어요", 관리 칸 모두 숨김 |
| `PostEditDeleteLikeE2ETest` | D | 회원 1·2, 블로그, 글 | 회원 2가 `#like-button` → `.like-count` 1·`aria-pressed=true` → 다시 → 0. 회원 1이 `#edit-link` → 제목·본문 수정 저장 → 바뀐 내용 → `#delete-button` → 확인 창 → `/blog/{slug}` 목록에 없음 |
| `BlogJoinE2ETest` | E | 회원 1·2, 회원 1의 공개·승인제 블로그 | 회원 2: `#join-button` "참여 신청" → `#join-state` "참여 신청 중", `#write-link` 숨김. 회원 1: 블로그 화면 `#request-list` 회원 2 줄의 "승인" → `#request-empty`. 회원 2 새로 고침 → `#blog-meta` "나: 멤버" → `#write-link` → 글 저장 성공 |
| `PostImageE2ETest` | F | 회원 1, 블로그 | **진짜 에디터**(CDN 허용): 툴바 `button.image` → `#toastuiImageFileInput`에 PNG(`TestImages.png()`) → `.toastui-editor-ok-button` → 저장 → `#post-body img[src^="/files/"]`가 보이고 그 주소가 200 |
| `AccountE2ETest` | G | 회원 1(블로그 없음) | 틀린 비밀번호 → `#login-message`. 로그인 → `/account.html` `#edit-start` → `#reauth-password` → `#new-password`·`#new-password-confirm` → `#password-message` ok → 로그아웃 → 옛 비밀번호 실패·새 비밀번호 성공 → `/withdraw.html` `#withdraw-agree`·`#withdraw-password` → `#withdraw-submit` → `/` 비로그인 → 다시 로그인 실패 |

- 에디터: B·D·E는 CDN을 막고 텍스트 칸(`#content`)으로 써서 바깥 사정에 흔들리지 않게 하고, 에디터 자체는 F가 확인한다. Toast UI 3.2.2 선택자는 npm 패키지 원본에서 확인함(`toastuiImageFileInput`, `ok-button`, 툴바 `className: 'image'`, 기본 탭 file)
- 회원 둘이 필요한 흐름(C·D·E)은 BrowserContext를 둘 쓴다
- 지켜야 할 서버 규칙: 비밀번호 `Abcd123!` 형식, 닉네임 2~12자, 슬러그 3~30자 소문자, 같은 이메일 인증번호 재요청은 60초 뒤(흐름마다 새 이메일), 로그인 5번 틀리면 잠김(G는 1번만 틀림), 블로그장은 탈퇴 불가(G는 블로그 없는 회원)

### ci.yml `full` 순서
전체 테스트(`test`) → **E2E**(`playwrightInstall e2eTest`, 실패 시 원인 표시 + `e2e-artifacts` 올림) → 커버리지 보고서(`jacocoTestReport`) → SonarQube 연결 확인·분석(push만) → jar → Docker
