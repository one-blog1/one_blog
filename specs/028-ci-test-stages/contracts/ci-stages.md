# Contract: CI 단계 (workflow `CI`)

다른 곳(배포 workflow, 브랜치 보호, 개발자)이 기대하는 약속. 이름을 바꾸면 이 문서와 그곳을 함께 고친다.

## 언제 무엇이 도는지

| 이벤트 | 브랜치 | `unit` (1단계) | `full` (2단계) | `full` 안의 SonarQube |
|---|---|---|---|---|
| push | `feature/**`, `hotfix/**` | 돈다 | 건너뜀 | — |
| pull_request | 대상 `develop`, `main` (그 밖도) | 돈다 | 건너뜀 | — |
| push | `develop`, `main` | 건너뜀 | 돈다 | 돈다 (게이트 대기) |
| workflow_dispatch | 아무 브랜치 | 건너뜀 | 돈다 | 건너뜀 |

- push의 `paths-ignore: ['**.md', '.claude/**', '.specify/**']`는 그대로.
- `unit`: MySQL 없음. `./gradlew unitTest jacocoUnitTestReport`. 결과물 `unit-coverage`(HTML) 7일.
- `full`: MySQL 8.4 → `./gradlew test` → `./gradlew playwrightInstall e2eTest` → `./gradlew jacocoTestReport` → `./gradlew sonar`(조건부) → bootJar → jar 실행 확인 → Docker 실행 확인 → `one-blog-jar` 14일. 실패 시 원인 표시·`test-report` 올리기는 지금 그대로.

## 이름 (밖에서 기대하는 값)

| 이름 | 쓰는 곳 |
|---|---|
| workflow `CI` | `deploy.yml`의 `workflow_run.workflows: [CI]` |
| job `unit` | 브랜치 보호 필수 검사 (`develop`, `main`) |
| Gradle 작업 `test` | 전체 테스트 (readme, 로컬 습관) |
| Gradle 작업 `unitTest` | 단위 테스트만 (DB 없이) |
| Gradle 작업 `e2eTest`, `playwrightInstall` | E2E만(`...E2ETest`, 브라우저·테스트 DB 필요), 브라우저 설치 |
| 결과물 `e2e-artifacts` | E2E 실패 시 화면 캡처·trace (7일) |
| Secrets `SONAR_TOKEN`, `SONAR_HOST_URL` | `full`의 SonarQube 단계 (사용자 등록 완료) |
| SonarQube Project key `one-blog` | `build.gradle.kts`의 `sonar` 블록 |

## 결과의 의미

- `main` push의 CI가 `success` = `full` 성공(테스트·게이트·jar·Docker 모두) → 배포 workflow가 버전을 확인하고 배포.
- `develop` push의 CI가 실패(게이트 포함) → 새 feature 브랜치로 고쳐 다시 merge. 통과 전에는 버전을 올리지 않는다.
- 게이트 실패 로그에는 SonarQube 프로젝트 화면 주소가 찍힌다(스캐너 출력).

## 배포 (`deploy.sh`)에 더하는 약속

- 프록시 전환 직후 `smoke-check.sh http://127.0.0.1:<PROXY_PORT>` (`WAIT_SECONDS=30`).
- 실패 → upstream을 옛 색 포트로 되돌림 → 새 컨테이너는 `<이름>-failed`로 멈춰 둠 → `::error title=배포 실패::` 후 종료(태그 없음).
- `deploy.yml`은 `scripts/smoke-check.sh`도 서버의 `~/one-blog/`로 올린다.
