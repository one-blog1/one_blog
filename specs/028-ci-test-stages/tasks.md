# Tasks: 배포 단계별 테스트 (CI 단계 나누기 + 정적 분석)

**Input**: `specs/028-ci-test-stages/` (spec.md, plan.md, research.md, contracts/ci-stages.md, quickstart.md)

**Tests**: 앱 코드를 바꾸지 않으므로 새 단위 테스트는 없다. 확인은 CI 실행 결과(quickstart.md)로 한다. 이 작업 환경은 Gradle·Maven 저장소와 SonarQube 서버가 막혀 있어, 브랜치 push(사용자 확인 뒤) 후 Actions로 확인한다.

**DB**: 변경 없음. ERD·Flyway 손대지 않음.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일이고 앞 작업에 기대지 않아 함께 할 수 있음
- 경로는 저장소 기준

---

## Phase 1: Setup

- [x] T001 브랜치 `feature/ci-test-stages`가 최신 `develop`(0.19, 9083560) 위인지 확인 (`git log origin/develop..HEAD`에 스펙 문서 커밋만)

---

## Phase 2: Foundational (모든 이야기가 기대는 것)

- [x] T002 `build.gradle.kts` plugins에 `jacoco`, `id("org.sonarqube") version "<최신>"` 추가. 버전은 SonarScanner for Gradle 문서에서 다시 확인(research R3 기준 7.5.0.8588). 버전을 바꿀 때 지킬 점(Java 21, Gradle 8.4 이상)을 주석으로
- [x] T003 `build.gradle.kts`: `unitTest` 작업 등록 — `Test` 타입, `testClassesDirs`·`classpath`는 `test`와 같게, `exclude("**/*IntegrationTest*")`, 설명 주석(이름 규칙 R1). 기존 `tasks.withType<Test>` 설정(JUnit, 프로필 test, 시간대, 실패 로그)이 그대로 걸리는지 확인
- [x] T004 `build.gradle.kts`: 커버리지 보고서 두 개 — `jacocoTestReport`(test 결과, XML·HTML 켬), `jacocoUnitTestReport`(`JacocoReport` 등록, `unitTest` 실행 데이터, XML·HTML, 출력 `build/reports/jacoco/jacocoUnitTestReport`). 각 보고서가 해당 테스트 작업 뒤에만 돌게(`dependsOn`)

**Checkpoint**: `./gradlew unitTest jacocoUnitTestReport`가 DB 없이 13개 클래스만 돌고, `./gradlew test`는 45개 클래스 그대로 (CI에서 확인)

---

## Phase 3: US1 - 기능 브랜치에서 빠른 피드백 (P1) 🎯 MVP

**Goal**: `feature/*`·`hotfix/*` push와 PR에서 DB 없이 단위 테스트만 5분 안에

**Independent Test**: `feature/ci-test-stages` push 시 `unit`만 돌고 `full`은 건너뜀

- [x] T005 [US1] `.github/workflows/ci.yml`: 맨 위 설명 주석을 단계 표로 다시 씀(1단계·2단계·조건, D-140). `on`·`concurrency`는 그대로
- [x] T006 [US1] `.github/workflows/ci.yml`: job `unit` 추가 — `if`(contracts 표: PR 전부, push는 `feature/`·`hotfix/`), MySQL 서비스 없음, checkout·Java 21·Gradle 설정 → `./gradlew unitTest jacocoUnitTestReport --no-daemon` → 실패 시 원인 표시(기존 python 스크립트를 `unitTest` 결과 경로 `build/test-results/unitTest/`로) → `unit-coverage` HTML 올리기(7일, 성공·실패 모두). `timeout-minutes: 15`

**Checkpoint**: PR 검사에 `unit` 하나가 보이고, 브랜치 보호 필수 검사로 고를 수 있음

---

## Phase 4: US2 - 합칠 때 통합 확인 (P1)

**Goal**: `develop`·`main` push(=merge)와 직접 실행에서만 지금 CI 전체가 돈다. 배포 연결은 그대로

**Independent Test**: Actions → CI → Run workflow(이 브랜치)에서 `full`만 돌고 테스트 265개·jar·Docker 통과, SonarQube 단계는 건너뜀

- [x] T007 [US2] `.github/workflows/ci.yml`: 기존 job `build`를 `full`로 이름을 바꾸고 `if`(push는 `develop`·`main`, 그리고 `workflow_dispatch`) 추가. 테스트 단계를 `./gradlew test jacocoTestReport --no-daemon`으로. 나머지(MySQL, 실패 표시, 보고서, bootJar, jar 실행 확인, Docker 확인, jar 올리기)는 그대로
- [x] T008 [US2] `.github/workflows/deploy.yml` 확인만: `workflow_run: workflows: [CI]`·`conclusion == 'success' && event == 'push'` 조건이 job 분리 뒤에도 맞는지(건너뛴 `unit`은 실패가 아님). 바꿀 것 없으면 주석에 "CI의 full job 성공" 한 줄만

**Checkpoint**: `main` push → `full` 성공 → 배포 workflow가 지금처럼 이어짐

---

## Phase 5: US3 - 정적 분석과 품질 게이트 (P1)

**Goal**: `develop`·`main` push에서 전체 커버리지와 함께 SonarQube 서버로 분석, 게이트 실패면 CI 실패(→ `main` 배포 막힘)

**Independent Test**: `develop`에 merge 뒤 `full` 로그에 "QUALITY GATE STATUS: PASSED", 서버 `one-blog`에 분석·커버리지

- [x] T009 [US3] `build.gradle.kts`: `sonar { properties { ... } }` — `sonar.projectKey=one-blog`, `sonar.projectName=One Blog`, `sonar.projectVersion=${project.version}`, `sonar.qualitygate.wait=true`, `sonar.qualitygate.timeout=300`, `sonar.coverage.jacoco.xmlReportPaths=<jacocoTestReport XML 경로>`. 서버 주소·토큰은 넣지 않음(환경변수 `SONAR_HOST_URL`·`SONAR_TOKEN`)을 주석으로. 브랜치 속성은 넣지 않음(Community, R3)
- [x] T010 [US3] `.github/workflows/ci.yml` `full`: checkout에 `fetch-depth: 0`
- [x] T011 [US3] `.github/workflows/ci.yml` `full`: 테스트 다음에 "SonarQube 서버 연결 확인" 단계 — `if: github.event_name == 'push'`, `curl -sS -m 20 "$SONAR_HOST_URL/api/system/status"`가 `"status":"UP"`이 아니면 `::error title=SonarQube 서버::서버에 닿지 않습니다 (SONAR_HOST_URL, 서버 상태 확인)` 후 실패. Secrets가 비어 있으면 같은 방식으로 이름을 알려 주고 실패
- [x] T012 [US3] `.github/workflows/ci.yml` `full`: "SonarQube 분석·품질 게이트" 단계 — `if: github.event_name == 'push'`, `env: SONAR_TOKEN, SONAR_HOST_URL` (secrets), `./gradlew sonar --no-daemon`. 실패 시 `::error title=품질 게이트::`에 서버 프로젝트 주소(`$SONAR_HOST_URL/dashboard?id=one-blog`)와 "새 feature 브랜치에서 고쳐 다시 merge" 안내

**Checkpoint**: 게이트 실패 → `full` 실패 → `main`이면 배포 안 됨

---

## Phase 6: US4 - 운영 배포 뒤 확인과 자동 되돌리기 (P2)

**Goal**: 프록시 전환 직후 운영 스모크, 실패하면 옛 버전으로 자동 복귀

**Independent Test**: `deploy.sh` 리뷰(되돌리기 경로) + 정상 배포 로그에 "운영 확인 통과"

- [x] T013 [P] [US4] `.github/workflows/deploy.yml`: 서버 업로드(scp)에 `scripts/smoke-check.sh` 추가, 맨 위 설명 3)에 운영 스모크·되돌리기 한 줄
- [x] T014 [US4] `scripts/deploy.sh`: 지금 색의 포트 목록(`ACTIVE_PORTS`)을 3)에서 함께 정함(`ACTIVE_NAMES`와 같은 방식)
- [x] T015 [US4] `scripts/deploy.sh`: `rollback_proxy` 함수 — `ACTIVE_PORTS`가 있으면 `write_upstream` → `nginx -t` → `nginx -s reload` → 로그. 새 컨테이너를 멈추고 `<이름>-failed`로 이름 바꿈(4)의 실패 처리와 같은 규칙). 첫 배포(옛 색 없음)는 되돌릴 곳이 없다고 로그만
- [x] T016 [US4] `scripts/deploy.sh` 5) 전환 뒤: `WAIT_SECONDS=30 bash "$DIR/smoke-check.sh" "http://127.0.0.1:$PROXY_PORT"` → 통과면 "운영 확인 통과" 로그, 실패면 `rollback_proxy` 후 `fail "운영 확인에 실패해 옛 버전으로 되돌렸습니다 ..."`. `smoke-check.sh`가 서버에 없으면(예전 배포물) 경고만 하고 넘어감. 맨 위 구조 주석에 5-1) 단계 추가

**Checkpoint**: `bash -n scripts/deploy.sh` 통과, 되돌리기 경로 리뷰

---

## Phase 7: Polish

- [x] T017 [P] `readme.md`: "테스트" 절 — 이름 규칙(`*IntegrationTest`=DB 필요, 나머지=단위), `unitTest`/`test` 명령, 커버리지 보고서 위치. "CI 단계" 표(contracts 요약). "SonarQube" 절 — 서버 주소, 프로젝트 `one-blog`, 게이트 실패 시 고치는 순서, VS Code 연결 모드(개인 토큰은 CI용과 따로). "브랜치 보호 켜는 법"(`develop`·`main`, 필수 검사 `unit`)
- [x] T018 [P] `DEPLOY.md`: 배포 흐름에 CI `full`(게이트 포함) 성공 조건, 전환 뒤 운영 스모크·자동 되돌리기, 수동 확인 명령
- [x] T019 `CLAUDE.md`: 브랜치 절에 "PR은 `unit` 통과가 필수, 품질 게이트는 merge 뒤 `full`에서" 한 줄
- [x] T020 문서 저장소 `docs/requirements/07-implementation.md`: 구현 표에 "CI 단계·정적 분석 | 1단계 unit(PR) / 2단계 full + SonarQube Community(merge 뒤 게이트) / 운영 스모크·자동 되돌리기 | — | D-140" 줄
- [x] T021 문서 저장소 `docs/requirements/08-decision-log.md`: D-140 (결정·이유·이전 방식) 맨 위에 추가
- [ ] T022 커밋 정리 후 **사용자 확인 받고** 브랜치 push → `unit` 결과 확인(SC-001 시간) → Actions에서 직접 실행으로 `full` 확인(SonarQube 단계 건너뜀, 265개 통과)
- [ ] T023 사용자 확인 뒤 `develop`으로 PR → merge → `develop`의 `full`에서 서버 연결·분석·게이트 확인(quickstart 5). 사용자에게 브랜치 보호(`unit`) 켜기 요청
- [ ] T024 기능 마침: `specs/028-ci-test-stages/`를 문서 저장소 `specs/`로 옮김, 문서 저장소 push(사용자 확인 뒤)

---

## Dependencies

- T002 → T003 → T004 → (US1 T006, US2 T007, US3 T009)
- US1(T005~T006)과 US2(T007~T008)는 같은 파일이라 순서대로. US3의 ci.yml 작업(T010~T012)은 T007 뒤
- US4(T013~T016)는 CI와 독립 — Phase 2 뒤 언제든
- Polish T017~T021은 각 이야기 뒤, T022~T024는 맨 끝 순서대로

## Parallel

- T013(deploy.yml)과 US1~US3(ci.yml·build.gradle.kts)
- T017(readme)·T018(DEPLOY.md)

## Implementation Strategy

1. Phase 2 + US1 → push 뒤 `unit` 시간 확인 (가장 큰 효과: 기능 브랜치 CI가 빨라짐)
2. US2 → 직접 실행으로 `full` 확인
3. US3 → `develop` merge 때 처음 실제 분석 (첫 분석은 기준점이라 새 코드가 비어 게이트 통과 예상)
4. US4 → 다음 버전 배포 때 실제로 동작
