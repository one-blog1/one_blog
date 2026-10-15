# Quickstart: 단계별 테스트 확인 방법

## 준비 (사용자, 완료/남은 것)

- [x] SonarQube 서버 `http://s4.java21.net:9000`에 로컬 프로젝트 `one-blog`
- [x] GitHub Secrets `SONAR_TOKEN`, `SONAR_HOST_URL`
- [ ] (merge 뒤) 브랜치 보호: Settings → Branches → `develop`·`main` 규칙 → "Require status checks to pass" → `unit` 선택
- [ ] (선택) VS Code SonarQube for IDE 연결 모드: 서버 주소 + 개인용 토큰(CI용과 따로) → 프로젝트 `one-blog`에 묶기

## 로컬

```bash
./gradlew unitTest jacocoUnitTestReport   # DB 없이, 1분 안팎
open build/reports/jacoco/jacocoUnitTestReport/html/index.html

./gradlew test jacocoTestReport           # 전체 (로컬 MySQL one_blog_test 필요, 지금과 같음)
open build/reports/jacoco/test/html/index.html
```

기대: `unitTest`는 13개 클래스만 돈다. `test`는 45개 클래스(265개) 그대로.

```bash
./gradlew playwrightInstall               # 처음 한 번 (Chromium 설치)
./gradlew e2eTest                         # E2E 7개 흐름 (테스트 DB 필요, 화면 없이)
npx playwright show-trace build/e2e-artifacts/<클래스>/<테스트>.zip   # 실패한 흐름 다시 보기 (또는 trace.playwright.dev에 끌어다 놓기)
```

## CI 단계 확인 (브랜치 push 뒤)

1. `feature/ci-test-stages` push → Actions의 CI에 `unit`만 돌고 `full`은 건너뜀(회색). 5분 안.
2. 첫 확인용으로 Actions → CI → Run workflow(브랜치 `feature/ci-test-stages`) → `full`이 돌고 SonarQube 단계는 건너뜀. 테스트 265개·E2E 7개·jar·Docker 통과. E2E 단계 시간이 7분 안(SC-006).
3. 서버 연결 확인(T001): 같은 실행에서 `curl $SONAR_HOST_URL/api/system/status`가 `"status":"UP"`인지 본다.
4. `develop`으로 PR → PR 검사에 `unit` 표시.
5. merge 뒤 `develop` push → `full`에서 SonarQube 단계가 돌고 "QUALITY GATE STATUS: PASSED" 출력. 서버 화면 `one-blog`에 분석·커버리지가 보임.

## 게이트 실패 확인 (선택)

일부러 이슈가 나는 코드를 새 feature 브랜치에 넣고 merge → `develop`의 CI가 게이트 실패로 빨간색 → 고치는 커밋으로 다시 merge → 초록색. (서버 기본 게이트에 걸릴 만한 예: 새 코드에서 빈 catch 블록 여러 개)

## 운영 스모크·되돌리기 확인

- 정상 배포: Deploy 로그에 "운영 확인 통과" → 옛 컨테이너 종료 → 태그.
- 실패 흉내(서버에서, 배포 없이): `WAIT_SECONDS=5 ./smoke-check.sh http://127.0.0.1:8430`이 지금 버전에서 "모두 통과"인지 본다. 되돌리기 경로는 `deploy.sh`의 함수 단위로 리뷰한다(운영에서 일부러 실패 배포는 하지 않음).
