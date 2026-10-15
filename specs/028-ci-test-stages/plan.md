# Implementation Plan: 배포 단계별 테스트 (CI 단계 나누기 + 정적 분석)

**Branch**: `feature/ci-test-stages` | **Date**: 2026-10-15 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/028-ci-test-stages/spec.md` (2026-10-15 확인)

## Summary

CI 한 덩어리(매번 MySQL + 전체 테스트 + jar + Docker)를 둘로 나눈다. **1단계 `unit`**은 기능 브랜치 push와 PR에서 DB 없이 단위 테스트와 커버리지만 돌려 빨리 끝내고, PR merge의 필수 검사가 된다. **2단계 `full`**은 `develop`·`main`에 merge됐을 때 지금 CI 전체에 커버리지와 SonarQube 분석(품질 게이트 대기)을 더해 돈다. 게이트 실패는 CI 실패라 `main`이면 배포되지 않는다. 운영 배포에는 프록시 전환 직후 스모크와 실패 시 자동 되돌리기를 더한다.

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
build.gradle.kts                 # jacoco, org.sonarqube 플러그인 / unitTest 작업 / 커버리지 보고서 2개 / sonar 블록
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
