# Research: 배포 단계별 테스트

## R1. 테스트를 단위·통합으로 나누는 방법

- **Decision**: 이름 규칙으로 나눈다. `*IntegrationTest`는 통합(MySQL 필요), 나머지 `*Test`는 단위. Gradle에 `unitTest` 작업(`exclude("**/*IntegrationTest*")`)을 새로 만들고, 기존 `test`는 지금처럼 전체를 돈다.
- **Rationale**: 지금 테스트가 이미 이 규칙을 지킨다(통합 32개 클래스는 모두 `IntegrationTestSupport` 상속, 단위 13개는 Spring·DB를 쓰지 않음). 파일을 옮기거나 `@Tag`를 붙일 필요가 없다. `./gradlew test`가 전체라는 readme·CI 관례도 그대로 둔다.
- **Alternatives**: JUnit `@Tag("integration")` (45개 파일 수정, 빠뜨리면 1단계에 DB 테스트가 섞임), 소스 폴더 분리 `src/integrationTest` (Gradle 설정·IDE 설정이 커짐). 둘 다 지금 규모에 과함(constitution V).

## R2. 커버리지

- **Decision**: Gradle `jacoco` 플러그인. `unitTest` → `jacocoUnitTestReport`(XML·HTML), `test` → `jacocoTestReport`(XML·HTML). SonarQube에는 `jacocoTestReport`의 XML을 `sonar.coverage.jacoco.xmlReportPaths`로 넘긴다.
- **Rationale**: Java 표준 도구이고 Gradle 기본 플러그인이라 버전 관리가 필요 없다. SonarQube가 XML 형식만 읽는다.
- **Alternatives**: 없음(사실상 표준).

## R3. SonarQube 서버 (Community) 분석 방법

- **Decision**: SonarScanner for Gradle 플러그인 `org.sonarqube`(문서 기준 최신 7.5.0.8588, 2026-09-02. 구현 때 다시 확인). `./gradlew sonar`를 2단계의 `develop`·`main` push에서만 돌린다.
  - `sonar.projectKey=one-blog`, `sonar.projectName=One Blog` (설정 파일, 비밀 아님)
  - 서버 주소·토큰은 환경변수 `SONAR_HOST_URL`·`SONAR_TOKEN`(GitHub Secrets). 스캐너가 이 이름의 환경변수를 바로 읽는다
  - `sonar.qualitygate.wait=true`, `sonar.qualitygate.timeout=300`: 서버가 게이트를 계산할 때까지 기다렸다가 실패면 작업을 실패시킨다 (Q3, merge 뒤 게이트)
  - `sonar.projectVersion` = `build.gradle.kts`의 `version`(예 0.19). 서버의 새 코드 기준이 기본값("이전 버전")이면, 새 코드 = 이번 버전 동안 `develop`에 쌓인 변경. 버전을 올리면 기준이 넘어간다. 첫 분석에는 이전 분석이 없어 새 코드가 비어 기존 코드 이슈로 바로 실패하지 않는다 (US3-4)
  - 브랜치 속성(`sonar.branch.name`)은 넣지 않는다. Community는 브랜치 하나라 넣으면 분석이 실패한다
  - checkout은 `fetch-depth: 0`(전체 기록). 얕은 복제면 SonarQube가 줄별 작성 기록(blame)을 못 읽어 새 코드 판단이 틀린다
- **Rationale**: Gradle 프로젝트는 Gradle 플러그인이 컴파일 결과·테스트·커버리지 경로를 알아서 넘긴다. CLI 스캐너보다 설정이 적다. 서버 요구: 스캐너 실행에 Java 21(이미 CI가 21), Gradle 8.4 이상(지금 8.14.3).
- **Alternatives**: SonarQube Cloud(사용자가 받은 서버를 쓰기로 함), sonar-scanner CLI 액션(컴파일 결과 경로를 직접 넘겨야 함), PR마다 분석(Community는 PR 분석이 없어 `develop` 결과를 덮어씀).
- **확인 못 한 것**: 이 작업 환경은 `s4.java21.net:9000`에 접속이 막혀 있다(공용 도메인, http). GitHub Actions에서 닿는지는 구현 첫 작업(T001)에서 Actions로 확인한다. 개발 DB(`s4.java21.net:13306`)가 같은 호스트이고 밖에서 쓰고 있어 닿을 가능성이 높다.

## R4. CI 단계 나누기

- **Decision**: workflow 이름 `CI`는 그대로 두고(배포 workflow가 이 이름을 기다림) job을 둘로 나눈다.
  - `unit` (1단계): `feature/**`·`hotfix/**` push, 모든 PR. MySQL 없이 `./gradlew unitTest jacocoUnitTestReport`. 보고서를 Actions 결과로 올림
  - `full` (2단계): `develop`·`main` push, 직접 실행. MySQL → `./gradlew test jacocoTestReport` → (`develop`·`main` push일 때만) `./gradlew sonar` → jar 실행 확인 → Docker 실행 확인. 지금 job 내용 그대로 + 커버리지·분석
  - 같은 프로젝트에 분석이 동시에 올라가는 경우(버전 merge 직후 `develop`·`main`)는 서버가 프로젝트별로 순서대로 처리하므로 따로 막지 않는다. 브랜치별 `concurrency`(지금 설정)는 그대로
  - 각 job은 `if:`로 이벤트·브랜치를 보고 건너뛴다. 건너뛴 job은 실패가 아니므로 workflow 전체 결론은 돈 job으로 정해진다 → `main` push는 `full` 성공 시에만 `success` → 배포 workflow 조건(`workflow_run.conclusion == 'success' && event == 'push'`) 변경 없음
- **Rationale**: 배포 연결(`workflow_run: workflows: [CI]`)을 건드리지 않는 가장 작은 변경. 브랜치 보호의 필수 검사 이름도 job 이름 하나(`unit`)로 단순.
- **Alternatives**: workflow 파일 두 개(배포의 `workflow_run` 대상과 필수 검사 이름을 함께 바꿔야 함), 단계마다 job을 더 잘게(구조만 복잡).

## R5. merge 막기 (브랜치 보호)

- **Decision**: `develop`·`main`의 브랜치 보호 규칙에 필수 상태 검사 `unit`을 건다. 사용자가 GitHub 설정에서 켠다(관리자 권한). readme에 순서를 적는다.
- **Rationale**: PR merge 전에 막을 수 있는 것은 1단계뿐(Community라 PR 분석 없음). 게이트는 2단계에서 CI 실패·배포 막기로 건다 (스펙 Q3 결정).

## R6. 운영 스모크와 자동 되돌리기

- **Decision**: `deploy.sh` 5) 프록시 전환 직후, 기존 `smoke-check.sh`를 프록시 주소(`http://127.0.0.1:8430`)로 `WAIT_SECONDS=30`으로 돈다. 실패하면:
  1. 옛 색이 있었으면 `upstream.conf`를 옛 색 포트로 되돌리고 `nginx -s reload`
  2. 새 컨테이너를 멈추고 `-failed` 이름으로 남김(지금 실패 처리와 같은 규칙)
  3. 배포 실패로 끝냄(태그 안 붙음)
  옛 컨테이너는 6) 정리 전이라 아직 떠 있다. 첫 배포(옛 색 없음)는 되돌릴 곳이 없어 실패만 알린다.
- `deploy.yml`의 서버 업로드 목록에 `scripts/smoke-check.sh`를 더한다.
- **Rationale**: CI와 같은 확인 스크립트를 운영에도 쓰면 확인 기준이 하나다. 9개 주소는 로그인·쓰기 없이 읽기만 해서 운영에 돌려도 안전하다.
- **Alternatives**: 카나리(스펙 Q2에서 제외), 바깥 도메인으로 확인(바깥 nginx·DNS 문제까지 섞여 되돌리기 판단이 흐려짐).

## R7. 이 작업 환경의 제약

- Gradle 배포판·Maven 저장소가 막혀 있어 여기서는 빌드·테스트를 돌릴 수 없다. 027처럼 브랜치를 push한 뒤 CI 결과로 확인한다(push는 사용자 확인 뒤).

## R8. E2E 테스트 도구와 구조 (US5, 2026-10-15 추가)

- **Decision**: Playwright for Java (`com.microsoft.playwright:playwright` 1.63.0, 2026-09-16 기준 최신)를 JUnit 5 테스트로 쓴다.
  - 서버: `@SpringBootTest(webEnvironment = RANDOM_PORT)`로 테스트 안에서 실제 서버를 띄우고, 브라우저(Chromium, 화면 없이)가 `http://localhost:<포트>`의 화면을 연다. DB는 통합 테스트와 같은 테스트 DB(CI의 MySQL 서비스).
  - 준비 코드 `E2ETestSupport`: 통합 테스트처럼 테스트마다 회원·인증 테이블을 비우고, 메일은 가짜(`MailService` 목)로 바꿔 보낸 인증번호를 읽는다. 흐름 A(가입) 말고는 회원·블로그를 화면 대신 빠른 길(API·저장소)로 미리 만들어 흐름 하나에 필요한 화면만 누른다.
  - 이름 규칙 `...E2ETest`. Gradle `e2eTest` 작업만 이것을 돌리고, `test`·`unitTest`에서는 뺀다(브라우저가 필요해 로컬 전체 테스트가 무거워지지 않게).
  - 브라우저 설치: Gradle `playwrightInstall` 작업(Playwright CLI `install --with-deps chromium`). CI에서 E2E 전에 한 번.
  - 요소는 문구가 아니라 id로 찾는다. 기다림은 Playwright의 자동 대기(요소가 보이고 누를 수 있을 때까지)를 쓰고 고정 `sleep`은 쓰지 않는다.
  - 실패하면 그 테스트의 화면 캡처와 기록(trace)을 `build/e2e-artifacts/`에 남겨 CI 결과물로 올린다.
- **Rationale**: 기존 테스트가 Java·JUnit·Gradle이고, 테스트 DB 비우기·가짜 메일 같은 준비 코드를 그대로 쓸 수 있다. Node 도구 묶음(package.json 등)을 저장소에 새로 두지 않는다(constitution V). 서버를 테스트가 직접 띄워 따로 jar를 띄우고 기다리는 스크립트가 필요 없다.
- **Alternatives**: Playwright(Node) + 띄운 jar(도구 두 벌), Selenium(대기 처리를 직접 해야 해서 잘 깨짐), Cypress(Node, Chromium 계열만, 다중 탭·회원 둘 흐름이 불편).
- **커버리지**: `e2eTest`도 JaCoCo 기록을 남기고, `jacocoTestReport`가 `test`·`e2eTest` 기록을 합쳐 SonarQube에 넘긴다. 그래서 CI 순서는 전체 테스트 → E2E → 커버리지 보고서 → SonarQube.
