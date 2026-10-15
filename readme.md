# One Blog

회원이 블로그를 만들고 함께 운영하는 블로그 플랫폼입니다. 메인블로그에서 회원가입·로그인·블로그 생성·참여를 하고, 실제 글쓰기는 회원이 만든 개별 블로그에서 합니다.

## 문서

이 저장소는 소스 코드만 둡니다(배포용). 요구사항과 기능 명세는 문서 저장소 [one-blog1/one-blog_docs](https://github.com/one-blog1/one-blog_docs)에 있습니다. 코드 주석의 `specs/...`, `docs/...`는 그 저장소의 경로입니다.

- 요구사항 분석서: [docs/requirements](https://github.com/one-blog1/one-blog_docs/tree/main/docs/requirements)
- 기능 로드맵(개발 순서): [docs/roadmap.md](https://github.com/one-blog1/one-blog_docs/blob/main/docs/roadmap.md)
- 기능별 명세: [specs](https://github.com/one-blog1/one-blog_docs/tree/main/specs)
- 프로젝트 원칙(Spec Kit constitution): [.specify/memory/constitution.md](.specify/memory/constitution.md)
- 배포 안내(버전, 환경변수, 확인 방법): [DEPLOY.md](DEPLOY.md)

## 개발 방식

[GitHub Spec Kit](https://github.com/github/spec-kit)으로 기능 단위 명세 → 계획 → 작업 → 구현 순서로 개발합니다. Claude Code에서 아래 명령을 씁니다.

| 순서 | 명령 | 하는 일 |
|---|---|---|
| 1 | `/speckit-specify` | 기능 명세 작성 (요구사항 ID를 근거로) |
| (선택) | `/speckit-clarify` | 모호한 부분을 질문으로 정리 |
| 2 | `/speckit-plan` | 기술 계획, 데이터 모델 작성 |
| 3 | `/speckit-tasks` | 구현 작업 목록으로 쪼개기 |
| (선택) | `/speckit-analyze` | 명세·계획·작업 사이 일관성 점검 |
| 4 | `/speckit-implement` | 작업 목록대로 구현 |

specify·plan·tasks는 단계마다 결과를 보고 확인한 뒤 다음 단계로 갑니다 (D-137). 결과물은 기능별로 `specs/<번호>-<기능명>/` 아래에 생깁니다. 기능을 마치면 그 폴더를 문서 저장소의 `specs/`로 옮기고 이 저장소에서는 지웁니다.

브랜치는 `feature/<영어 기능이름>` → `develop` → (버전을 올릴 때) `main`이고, 운영 버그는 `hotfix/<이름>`을 main에서 만들어 main·develop 둘 다에 merge합니다 (D-136). 배포는 main에서 버전을 올렸을 때만 합니다 ([DEPLOY.md](DEPLOY.md)).

## 기술 스택

Spring Boot 4.1 · Java 21 · Spring Security · MySQL 8.4 · Flyway · 정적 HTML + JS · Toast UI Editor (자세한 내용은 요구사항 7장)

### 코드 위치

- 서버(`src/main/java/com/oneblog/`): 기능별 패키지(auth, member, blog, post, comment, notification, admin, sanction …). 공통은 `common/`. 뒤에 붙는 기능은 앞 기능이 열어 둔 자리(`PostExtension`, `JoinGate`, `FollowGate`, `SearchFilter`, `*Listener`)를 구현해 끼어들고, 앞 기능의 코드는 고치지 않습니다.
- 화면(`src/main/resources/static/`): HTML은 화면마다 하나. 스크립트는 `js/lib/`(모든 화면이 싣는 공통: api, ui, header, modal, theme, icons. 글쓰기 화면만 싣는 로그인 연장 session-keeper도 여기 있음), `js/components/`(여러 화면이 같이 쓰는 부품: blog-card, post-item, pager, report …), `js/pages/`(화면 하나에만 쓰는 것. 파일이 여럿이면 `pages/blog/`처럼 폴더). 날짜 표시·글 주소 같은 공통 도우미는 `js/lib/ui.js`에만 둡니다.
- 스타일(`css/app.css`): 파일 맨 위 주석의 구성(바탕 → 공통 부품 → 화면별 → 가로지르는 규칙) 순서를 지킵니다. 새 화면의 규칙은 그 화면 자리에 넣고 파일 끝에 덧붙이지 않습니다.

## 로컬 실행

필요한 것: JDK 21, MySQL 8.4(테스트용). Spring Boot와 Gradle은 `./gradlew`가 처음 실행할 때 자동으로 받습니다.

- 개발 DB: Crowfoot이 발급한 MySQL(`s4.java21.net:13306`, `cf_u36_d1`). ERD 문서와 연결돼 있고, 테이블은 앱이 시작할 때 Flyway가 만듭니다. Crowfoot의 "변경 반영"으로 테이블을 만들지 않습니다 (constitution IV).
- 테스트 DB: 테스트는 실행마다 테이블을 비우므로 개발 DB와 다른 DB를 씁니다. 기본은 로컬 MySQL의 `one_blog_test`.

1. 테스트 DB와 계정을 만듭니다 (로컬 MySQL root로 한 번만).
   ```sql
   CREATE DATABASE one_blog_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   CREATE USER 'one_blog'@'localhost' IDENTIFIED BY '원하는_비밀번호';
   GRANT ALL PRIVILEGES ON one_blog_test.* TO 'one_blog'@'localhost';
   ```
2. 환경변수 파일을 만들고 값을 채웁니다. `.env`는 저장소에 올라가지 않습니다.
   ```bash
   cp .env.example .env
   ```
   `DB_USERNAME`, `DB_PASSWORD`는 Crowfoot 데이터베이스 탭의 값, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`는 1번에서 만든 로컬 계정입니다.
3. 테스트와 실행
   ```bash
   ./gradlew unitTest   # 단위 테스트만, DB 없이 빠르게
   ./gradlew test       # 전체 (1번의 테스트 DB 필요)
   ./gradlew bootRun
   ```
   http://localhost:8080 에 접속합니다. `MAIL_MODE=log`이면 인증번호가 메일 대신 서버 로그에 찍힙니다.

블로그 대표 이미지 같은 업로드 파일은 `FILE_STORAGE_DIR`(기본 `./uploads`)에 저장되고 저장소에는 올라가지 않습니다. 블로그 생성 개수는 `BLOG_LIMIT_PUBLIC`(기본 3), `BLOG_LIMIT_PRIVATE`(기본 5)로 바꿀 수 있습니다.

## 테스트와 CI

**테스트 이름 규칙** (D-140): 이름이 `...IntegrationTest`로 끝나면 서버와 테스트 DB(MySQL)를 띄우는 통합 테스트, 나머지 `...Test`는 DB 없는 단위 테스트입니다. DB가 필요한 테스트를 새로 만들면 이름을 `IntegrationTest`로 끝내야 CI 1단계에서 빠집니다. 테스트 코드는 `src/test/java/com/oneblog/` 아래에 기능 코드와 같은 폴더 구조로 있습니다.

| 명령 | 도는 테스트 | 커버리지 보고서 |
|---|---|---|
| `./gradlew unitTest jacocoUnitTestReport` | 단위만 | `build/reports/jacoco/jacocoUnitTestReport/html/index.html` |
| `./gradlew test jacocoTestReport` | 전체 | `build/reports/jacoco/test/html/index.html` |

테스트 결과 화면은 `build/reports/tests/<unitTest|test>/index.html`입니다.

**CI 단계** (`.github/workflows/ci.yml`, D-140)

| 단계 | 언제 | job | 하는 일 |
|---|---|---|---|
| 1 빠른 확인 | `feature/**`·`hotfix/**` push, 모든 PR | `unit` | 단위 테스트 + 커버리지. 실패하면 PR merge가 막힘(브랜치 보호) |
| 2 통합 확인 | `develop`·`main`에 merge(push), 직접 실행 | `full` | 전체 테스트 + 커버리지 + SonarQube 품질 게이트(push만) + jar·Docker 실행 확인 |
| 운영 | `main`에서 버전을 올렸을 때 | Deploy | 전환 직후 운영 확인, 실패하면 옛 버전으로 자동 되돌림 ([DEPLOY.md](DEPLOY.md)) |

기능 브랜치에서 통합 테스트를 미리 돌려 보려면 Actions → CI → Run workflow에서 그 브랜치를 고릅니다(SonarQube에는 올리지 않음).

**SonarQube** (정적 분석): 서버 `http://s4.java21.net:9000`, 프로젝트 `one-blog`. 서버가 Community라 PR은 분석하지 않고, `develop`·`main`에 merge된 코드를 2단계에서 분석합니다.
- 결과 보기: 서버에 로그인 → 프로젝트 `one-blog` → Issues(이슈, 심각도별), Measures(커버리지·중복), Security Hotspots. 이슈를 누르면 해당 줄과 고치는 방법이 나옵니다. 문제가 아닌 이슈는 "False Positive"·"Accept"로 표시합니다.
- **품질 게이트 실패**: `develop`·`main`의 CI가 빨간색이 되고 `main`이면 배포되지 않습니다. 서버에서 새 이슈를 보고, 새 `feature` 브랜치에서 고쳐 다시 merge합니다. 게이트를 통과하기 전에는 버전을 올리지 않습니다.
- 게이트는 "새 코드" 기준입니다. 분석의 버전이 서비스 버전이라, 새 코드 = 이번 버전 동안 쌓인 변경입니다(서버의 새 코드 기준이 "이전 버전"일 때). 게이트 조건은 서버 관리자가 정합니다.
- 비밀값: GitHub Secrets `SONAR_TOKEN`(이 프로젝트 분석 전용 토큰), `SONAR_HOST_URL`. 서버가 http라 토큰이 암호화 없이 오가므로 다른 권한이 있는 토큰은 쓰지 않고, 의심되면 서버의 My Account → Security에서 폐기·재발급합니다.
- **코드 쓰는 중에 미리 보기**: VS Code의 SonarQube for IDE 확장(Java는 Red Hat "Language Support for Java" 확장도 필요)에서 SonarQube Server 연결을 추가하고(서버 주소 + 개인 토큰, CI용과 따로) 프로젝트 `one-blog`에 묶으면 서버와 같은 규칙으로 바로 검사합니다.

**브랜치 보호** (저장소 관리자가 한 번 켬): Settings → Rules → Rulesets → New branch ruleset
1. Enforcement status: Active, Target branches: `develop`, `main`
2. Bypass list: `Repository admin`과 Claude 세션이 push할 때 쓰는 Claude GitHub 앱 (update 스킬이 `develop`·`main`에 직접 push하므로 꼭 넣습니다)
3. Rules: **Require status checks to pass** → `unit` (CI가 한 번 돈 뒤에 목록에 나옵니다). "Require a pull request before merging"은 켜지 않습니다

검증 시나리오는 문서 저장소의 기능별 quickstart를 봅니다: [001 회원가입·로그인](https://github.com/one-blog1/one-blog_docs/blob/main/specs/001-signup-login/quickstart.md), [002 블로그 생성·목록](https://github.com/one-blog1/one-blog_docs/blob/main/specs/002-blog-create-list/quickstart.md).
