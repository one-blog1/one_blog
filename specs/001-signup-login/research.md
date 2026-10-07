# Research: 001 회원가입·로그인·로그아웃

기술 선택은 요구사항 7장과 constitution에서 대부분 확정돼 있다. 여기서는 7장이 정하지 않은 구현 세부만 정한다.

## R1. 프레임워크 버전

- **Decision**: Spring Boot 4.1.x, Java 21(Temurin), Gradle Kotlin DSL(Wrapper 포함)
- **Rationale**: 4.1.x가 현재 최신이고 Java 17~26을 지원한다. 개발 맥에 Temurin 21이 설치돼 있다. Boot 4는 스타터가 모듈로 나뉘어 `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, `spring-boot-starter-webmvc-test`처럼 기능별 스타터를 직접 넣는다. Jackson 3(`tools.jackson` 패키지)이 기본이다.
- **Alternatives considered**: Boot 3.5 — 자료는 많지만 새 프로젝트를 이전 세대로 시작할 이유가 없다.

## R2. JWT 발급·검증 방식 (D-53)

- **Decision**: 외부 JWT 라이브러리 없이 Spring Security의 OAuth2 Resource Server(Nimbus `JwtEncoder`/`JwtDecoder`)를 쓴다. 서명은 HS256, 키는 환경변수 `JWT_SECRET`(32바이트 이상). 토큰은 쿠키에서 읽도록 `BearerTokenResolver`를 쿠키용으로 바꾼다.
- **Rationale**: Spring Security에 포함된 검증된 구현이라 의존성이 하나 줄고, 서명·만료 검증을 프레임워크가 처리한다 (D-09 "검증된 코드").
- **Alternatives considered**: JJWT — 널리 쓰이지만 같은 일을 하는 라이브러리를 하나 더 들여온다. 비대칭 키(RS256) — 서버 1대에서는 이점이 없다.

## R3. 토큰 구성과 "로그인 유지" (SEC-04, D-61, D-62, D-82)

- **Decision**:
  - Access Token: 유효 10분, 클레임은 `sub`(회원 ID)와 `sid`(refresh_tokens 행 ID)만. 쿠키 `ACCESS_TOKEN`.
  - Refresh Token: 무작위 256비트 값, DB에는 SHA-256 해시만 저장. 쿠키 `REFRESH_TOKEN`(Path `/api/auth`).
  - 쿠키 공통: `HttpOnly`, `Secure`, `SameSite=Lax`. 로그인 유지 미체크면 Max-Age 없는 세션 쿠키, 체크면 Max-Age 14일.
  - `refresh_tokens.expires_at`이 만료의 단일 기준이다. 체크 시 로그인 시각 + 14일로 고정. 미체크 시 `last_activity_at + 30분`이고 활동할 때마다 함께 민다.
  - 인증된 모든 요청에서 필터가 `sid`로 refresh_tokens 행과 회원을 읽어, 폐기·만료·탈퇴 상태면 거부한다.
  - 30분 무활동: 화면의 `api.js`가 사용자가 직접 한 행동에만 `X-User-Activity: 1` 헤더를 붙인다. 필터는 이 헤더가 있고 마지막 갱신이 1분 이상 지났을 때만 `last_activity_at`·`expires_at`을 갱신한다.
  - Refresh Token 교체(rotation)는 1차에 하지 않는다.
- **Rationale**: constitution III과 D-53이 "정지·역할·로그아웃은 토큰만 믿지 말고 요청마다 DB 확인"을 요구한다. 어차피 매 요청 DB를 읽으므로, 그 행으로 로그아웃·30분 무활동을 즉시 반영한다(SC-005, SC-007). 갱신을 1분 간격으로 줄여 쓰기 부하를 줄인다.
- **Alternatives considered**: Access Token만 믿고 만료를 짧게 — 로그아웃이 최대 만료 시간만큼 늦게 반영돼 USR-04를 못 지킨다. 활동 시각을 메모리에 저장 — SCL-01 위반.

## R4. CSRF (SEC-10)

- **Decision**: Spring Security의 `csrf(csrf -> csrf.spa())`를 쓴다. `XSRF-TOKEN` 쿠키(JS가 읽을 수 있음)를 내려주고, `api.js`가 모든 상태 변경 요청에 `X-XSRF-TOKEN` 헤더를 붙인다.
- **Rationale**: 7장 화면 방식 결정 그대로이고, SPA용 기본 설정이 프레임워크에 있다.
- **Alternatives considered**: CSRF 끄고 SameSite만 의존 — 쿠키 인증이라 SEC-10 위반.

## R5. 이메일 인증번호 저장과 가입 단계 연결 (USR-02, D-28)

- **Decision**:
  - 인증번호는 `SecureRandom`으로 6자리를 만들고, 서버 비밀값(`CODE_PEPPER`)으로 HMAC-SHA256 해시해 `verification_codes.code_hash`에 저장한다.
  - 이미 가입된 이메일도 똑같이 행을 만들고(무작위 번호, 발송은 안내 메일) 같은 응답을 준다. 그래서 1분 재발송 제한과 응답 형태가 가입 여부와 무관하게 같다.
  - 메일 발송은 비동기(`@Async`)로 해 응답 시간으로 가입 여부를 알 수 없게 한다.
  - 인증에 성공하면 `verified_at`을 기록하고, 서버가 서명한 30분짜리 가입 티켓(JWT, 클레임: 이메일, 인증 행 ID, 용도 `SIGNUP`)을 HttpOnly 쿠키 `SIGNUP_TICKET`(Path `/api/auth/signup`)으로 내려준다.
  - 가입 완료 요청은 티켓 서명·만료를 검사하고, DB에서 그 행이 인증됐고 아직 쓰이지 않았는지(`used_at` NULL) 다시 확인한다. 가입이 끝나면 `used_at`을 기록한다.
- **Rationale**: ERD를 바꾸지 않고 "인증 단계 건너뛰기 방지"(USR-02 구현 순서 5)를 지킨다. 티켓이 없으면 이메일만 아는 다른 사람이 인증된 이메일로 가입을 가로챌 수 없다.
- **Alternatives considered**: 이메일만 보내고 서버가 최근 인증 여부만 확인 — 같은 30분 안에 다른 사람이 가로챌 수 있다. 가입 토큰 컬럼 추가 — ERD 변경이 필요하고 서명 토큰으로 충분하다.

## R6. 메일 발송 (D-69)

- **Decision**: `spring-boot-starter-mail`, Gmail SMTP(`smtp.gmail.com:587`, STARTTLS). 계정·앱 비밀번호는 환경변수 `MAIL_USERNAME`, `MAIL_PASSWORD`. 로컬 개발에서 메일 없이 돌릴 수 있게 `app.mail.mode=log` 설정이면 메일 대신 인증번호를 서버 로그에 남긴다(local 프로필에서만).
- **Rationale**: 7장 결정. 개발 중에는 Gmail 앱 비밀번호 없이도 가입 흐름을 시험할 수 있어야 한다.
- **Alternatives considered**: 로컬 SMTP 서버(MailHog 등) — 설치할 것이 하나 늘어난다.

## R7. 비밀번호 해시 (SEC-01, D-81)

- **Decision**: `BCryptPasswordEncoder`(강도 10). 비밀번호 최대 15자라 bcrypt의 72바이트 제한에 걸리지 않는다.
- **Rationale**: D-81 확정.

## R8. 테스트 방식

- **Decision**: JUnit 5 + Spring Boot Test. DB가 필요한 통합 테스트는 로컬 MySQL 8.4의 `one_blog_test` DB에 Flyway로 스키마를 만들고 테스트마다 정리한다. 메일은 테스트에서 가짜 `MailSender`로 바꿔 보낸 내용을 확인한다.
- **Rationale**: Docker 없이 이미 설치된 MySQL로 실제 DB와 같은 동작(CHECK 제약, utf8mb4 대소문자 무시 비교)을 시험한다 (constitution V 단순함).
- **Alternatives considered**: Testcontainers — 환경이 더 깨끗하지만 Docker Desktop 설치가 필요하다. 나중에 CI를 붙일 때 다시 검토한다. H2 — MySQL과 CHECK·콜레이션 동작이 달라 쓰지 않는다.

## R9. 화면 구성 (D-66, SEC-06)

- **Decision**: `src/main/resources/static`에 `index.html`, `signup.html`, `login.html`과 `js/api.js`, `js/signup.js`, `js/login.js`, `js/header.js`를 둔다. 사용자 입력은 모두 `textContent`로 넣는다. 가입 ①~③ 단계는 한 페이지 안에서 단계별로 보여준다.
- **Rationale**: 7장 화면 방식. 공통 `api.js` 한 곳에서 CSRF 헤더, 활동 헤더, 401 시 refresh 재시도를 처리해 빠뜨리지 않는다.
