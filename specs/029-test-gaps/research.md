# Research: 테스트 보강 (029)

코드를 읽어 정한 테스트 방식이다. 이 작업 공간은 Maven 저장소에 접속하지 못해 테스트를 직접 돌릴 수 없다. 그래서 아래 결정은 모두 코드와 기존 테스트를 읽고 내렸고, 확인은 CI에서 한다.

## R1. 테스트 회원의 전화번호·이름

- **결정**: `IntegrationTestSupport`에 `signUp(email, nickname, name, phone)` 오버로드를 더한다. 기존 `signUp(email, nickname)`은 지금 값(홍길동, 010-1234-5678)으로 이것을 부른다.
  - 블랙리스트·이메일 찾기 테스트는 회원마다 다른 번호를 쓴다.
  - 기존 강퇴 테스트(`BlogJoinIntegrationTest`, `ModerationIntegrationTest`)도 대상 회원과 다른 회원의 번호를 다르게 해서, 전화번호가 같아서 막히는 일이 없게 한다.
- **이유**: 기존 호출 수백 곳을 고치지 않고, 필요한 테스트만 값을 정한다.
- **버린 안**:
  - 회원마다 번호를 자동으로 다르게 만들기: 이메일 찾기 테스트가 "홍길동 + 010-1234-5678"에 기대고 있어 깨진다.
  - 기본값 바꾸기: 영향 범위가 너무 넓다.

## R2. 꺼 둔 기능을 켜는 테스트 (요청 제한·사람 확인·Secure 쿠키)

- **결정**: 새 클래스 `SecurityFeaturesIntegrationTest` 하나에서 세 기능을 함께 켠다.
  - 설정: `@TestPropertySource(properties = {"app.rate-limit.enabled=true", "app.cookie.secure=true"})`
  - 사람 확인: `@MockitoBean TurnstileVerifier`로 `enabled()=true`, `verify(token, ip)`는 토큰 값으로 참·거짓을 정한다.
- **이유**: 설정이 다른 테스트 클래스마다 Spring을 새로 띄운다(10~20초). 한 클래스로 모으면 한 번만 더 띄운다.
- **요청 제한 기록 섞임 방지**: `RateLimiter`는 기록을 앱 메모리에 둔다. 같은 클래스 안의 테스트끼리 기록이 섞이지 않게 테스트마다 다른 IP(`request.setRemoteAddr("10.29.<n>.1")`)를 쓴다.
- **버린 안**: 기능마다 클래스 하나씩. 띄우는 횟수가 3번 늘어난다.
- **발견 (범위 밖)**: `RateLimiter`의 기록은 앱마다 따로 있다. 운영은 앱 2개가 번갈아 받으므로, 같은 IP 로그인 제한(10분 30번)이 사실상 60번 안팎이 된다. SCL-01(서버 메모리에 상태를 두지 않음)과도 어긋난다. 이번에는 테스트하지 않고 사용자에게 알린다.

## R3. "정한 주소 외 거부"(denyAll) 확인

- **결정**: `SecurityConfig`의 `anyRequest().denyAll()`은 파일이 있는지 보기 전에 거부한다. 그래서 목록에 없는 주소는 파일이 없어도 404가 아니라 401·403이 나온다.
  - 비회원은 인증 실패 처리기(`unauthorizedEntryPoint`)가 401을 준다. 기존 `없는_화면은_막힌다`를 "401(404가 아님)"으로 바꾼다.
  - 같은 테스트에서 허용 목록에 있는 화면(`/login.html`)은 200인지 함께 본다.
- **버린 안**: 테스트용 정적 파일을 두고 막히는지 보기. 필요 없는 파일이 생긴다.

## R4. CSRF 실제 경로

- **결정**: `.with(csrf())`를 쓰지 않는 테스트를 `LoginLogoutIntegrationTest`에 더한다. 순서는 이렇다.
  1. GET 요청으로 `XSRF-TOKEN` 쿠키를 받는다. `CsrfCookieFilter`가 내려 준다.
  2. 같은 값을 쿠키와 `X-XSRF-TOKEN` 헤더에 넣어 로그인하면 성공한다.
  3. 헤더 값이 틀리거나 없으면 403이다.
- **이유**: 테스트 도구의 `csrf()`는 토큰 저장소를 바꿔서, 실제 쿠키·헤더 비교가 한 번도 실행되지 않는다.

## R5. 시간이 걸리는 규칙 (10분·30분·7일·30일·1년)

- **결정**: 기다리지 않는다.
  - 저장된 시각(`expires_at`, `created_at`, `deleted_at`, `suspended_until`, `locked_until` 등)을 과거로 바꾼다.
  - 배치는 `DailyBatch.runAll(기준 시각)`에 미래 시각을 넣는다. 지금 테스트가 쓰는 방식과 같다.
- **주의**: 서버가 "지금"을 JVM 시계로 읽는 곳과 DB 시계로 읽는 곳이 섞여 있다. 테스트는 그 코드가 읽는 쪽 시계를 기준으로 값을 넣는다. 기존 잠금 테스트의 방식을 따른다.

## R6. 영상 썸네일 리다이렉트 (SSRF 방어)

- **현황**: `HttpVideoThumbnailFetcher`는 생성자 안에서 HTTP 클라이언트(`RestClient`)를 직접 만든다. 그리고 허용 사이트의 https 443만 받는다. 그래서 테스트에서 가짜 서버로 리다이렉트를 흉내 낼 방법이 없다.
- **결정**: 패키지 안에서만 쓰는 생성자 `HttpVideoThumbnailFetcher(boolean enabled, RestClient client)`를 하나 더한다. 테스트는 `MockRestServiceServer`로 만든 클라이언트를 넣어 확인한다.
  - 허용 사이트 → 허용 밖 주소로 리다이렉트: 사진을 받지 않는다.
  - 리다이렉트 4번: 받지 않는다.
  - 2MB+1바이트: 받지 않는다.
  - 기존 공개 생성자와 동작은 그대로다.
- **이유**: 리플렉션으로 final 필드를 바꾸는 방법보다 덜 깨지고 읽기 쉽다.
- **스펙과의 관계**: 테스트를 위한 입구 하나라 동작은 바뀌지 않는다. 그래도 기능 코드 파일을 고치므로 plan 확인 때 함께 알린다.
- **2MB 제한의 서비스 쪽**: `VideoThumbnailService`가 받은 바이트 길이를 다시 본다. 이것은 기존 가짜 fetcher로 `VideoThumbnailIntegrationTest`에서 확인한다.

## R7. 04:00 배치 잠금 고치기 (FR-010)

- **현황** (`batch/SchedulerLock`, `DailyBatch.scheduled`)
  - `release`가 `lock_until`을 지금 시각으로 되돌린다. 그래서 늦게 온 다른 앱이 같은 날 잠금을 다시 잡을 수 있다.
  - 잠금 주인 이름은 `호스트이름:PID`다. 운영 컨테이너는 `--network host`라 호스트 이름이 같고, 둘 다 `java`가 PID 1이라 두 앱의 이름이 같다. 지금은 "잡은 쪽만 푼다"가 실제로 지켜지지 않는다.
- **결정**
  - `DailyBatch.scheduled()`가 시작 시각을 기억했다가, 끝나면 `release(name, 시작 + 5분)`을 부른다. `SchedulerLock.release`는 `lock_until = max(지금, 그 시각)`으로 둔다.
  - 잠금 주인 이름에 앱이 뜰 때 만든 무작위 값을 붙인다(`호스트:PID:8자리`). 그래야 다른 앱의 잠금을 풀지 못한다.
  - 테스트용으로 주인 이름을 받는 패키지 생성자를 둔다. Spring이 쓰는 기존 생성자에는 `@Autowired`를 붙인다.
- **테스트** (`SchedulerLockIntegrationTest`, 테스트 DB의 `shedlock` 테이블)
  - A가 잡으면 B는 못 잡는다.
  - A가 풀어도 5분 안에는 B가 못 잡는다. 이 테스트는 고치기 전 코드에서 실패한다.
  - 시간이 지나면 B가 잡는다.
  - 최대 1시간이 지나면 풀리지 않았어도 잡을 수 있다.
  - 잠겨 있으면 `scheduled()`가 배치를 건너뛴다. 확인 방법: 예정 시각이 지난 폐쇄 예정 블로그가 그대로 CLOSING이다.
  - `@Scheduled`의 cron은 `0 0 4 * * *`이고 zone은 `Asia/Seoul`이다.
- **버린 안**:
  - ShedLock 라이브러리: plan R4(012)에서 직접 두기로 했다.
  - 잠금을 풀지 않고 1시간 두기: 수동 실행이 막힌다.

## R8. 테스트를 둘 곳

- **결정**: 되도록 기존 클래스에 더한다. 같은 설정이면 Spring을 다시 띄우지 않는다.
  - 새로 만드는 클래스는 다섯 개다. 028 이름 규칙을 따른다.

    | 클래스 | 종류 | 다루는 것 |
    |---|---|---|
    | `SecurityFeaturesIntegrationTest` | 통합 | R2 |
    | `CrossBlogPermissionIntegrationTest` | 통합 | US2-1 |
    | `RetentionBatchIntegrationTest` | 통합 | US5 |
    | `SchedulerLockIntegrationTest` | 통합 | R7 |
    | `SignupPolicyTest` | 단위 | 비밀번호 규칙 경계값 |

  - 이름 가림 단위 테스트는 기존 `MaskingTest`에, 리다이렉트는 기존 `HttpVideoThumbnailFetcherTest`(단위)에 더한다.
- **이유**: 한 클래스가 너무 커지는 곳(권한 교차, 삭제 배치)만 따로 뺀다.

## R9. 테스트 실행과 확인 (CI)

- **결정**
  - 028이 합쳐지기 전: `feature/test-gaps`에 push하면 지금 CI가 전체 테스트를 돈다.
  - 028이 합쳐진 뒤: push는 단위 테스트만 돈다. 그래서 `gh workflow run ci.yml --ref feature/test-gaps`(직접 실행 = 전체 단계)로 통합 테스트를 확인한다.
  - SC-003(규칙을 깨면 실패) 확인: 임시 브랜치 `feature/test-gaps-mutation`에서 7개 규칙을 한 커밋으로 일부러 깬다. CI에서 해당 7개 테스트가 모두 실패하는지 본 뒤 브랜치를 지운다. 결과는 대조표에 적는다.
- **이유**: 여기서는 돌릴 수 없으므로 push 횟수를 줄이도록 묶는다.
- **push 확인**: 구현 단계에서 `feature/test-gaps`와 임시 브랜치에 CI용 push를 여러 번 해도 되는지 한 번에 확인받는다.

## R10. 요구사항과 코드가 이미 다른 것 (분석 6번)

- **결정**: 지금 코드 동작으로 테스트하고, 메서드 설명에 "요구사항과 다름(분석 6번)"을 적는다.
  - 예: 쓴 가입 인증번호와 이메일 찾기 토큰은 04:00 배치에서 지워진다 (4.5는 "쓰면 바로 삭제").
- **이유**: 어느 쪽으로 맞출지는 사용자가 정할 일이다. 테스트는 지금 동작이 바뀌면 알려 주는 역할만 한다.

## R11. 버그를 찾았을 때

- **결정**: CI에서 새 테스트가 실패하면 먼저 테스트가 틀렸는지 확인한다.
  - 코드가 요구사항과 다르면 그 테스트를 요구사항 기준으로 두고 사용자에게 알린다.
  - 사용자가 고치기로 하면 같은 브랜치에서 고치고 결정 기록을 남긴다.
  - 따로 하기로 하면 `@Disabled("이유, 후속 작업")`로 두고 대조표에 적는다.
