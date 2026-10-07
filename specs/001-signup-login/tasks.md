---
description: "Task list for 001 회원가입·로그인·로그아웃"
---

# Tasks: 회원가입·로그인·로그아웃

**Input**: Design documents from `/specs/001-signup-login/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/auth-api.md](contracts/auth-api.md), [quickstart.md](quickstart.md)

**Tests**: constitution 개발 흐름이 요구하는 "권한 없는 요청이 거부되는지" 테스트와, spec의 보안 규칙(인증번호 만료·5회·재발송, 가입 여부 비노출)을 확인하는 통합 테스트만 넣는다. 그 밖의 테스트는 만들지 않는다.

**Organization**: 사용자 스토리별로 나눠, 각 스토리를 따로 구현하고 시험할 수 있게 한다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일이고 아직 끝나지 않은 작업에 의존하지 않아 병렬로 할 수 있음
- **[Story]**: 사용자 스토리 (US1, US2, US3)

## Path Conventions

- 애플리케이션 코드: `src/main/java/com/oneblog/`
- 설정·마이그레이션·화면: `src/main/resources/`
- 테스트: `src/test/java/com/oneblog/`, `src/test/resources/`

---

## Phase 1: Setup (프로젝트 초기화)

**Purpose**: 저장소 루트에 Spring Boot 프로젝트 뼈대를 만든다 (plan.md Project Structure).

- [X] T001 Gradle Wrapper(Gradle 8.14.3)와 `settings.gradle.kts`(rootProject.name = "one-blog")를 저장소 루트에 만든다: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, `settings.gradle.kts`
- [X] T002 `build.gradle.kts`를 만든다: Spring Boot 플러그인 4.1.x, `io.spring.dependency-management`, Java toolchain 21, group `com.oneblog`. 의존성: `spring-boot-starter-webmvc`, `spring-boot-starter-security`, `org.springframework.security:spring-security-oauth2-jose`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-mail`, `spring-boot-starter-flyway`, `org.flywaydb:flyway-mysql`, runtimeOnly `com.mysql:mysql-connector-j`; 테스트 `spring-boot-starter-test`, `spring-boot-starter-webmvc-test`, `org.springframework.security:spring-security-test`. 외부 JWT 라이브러리는 넣지 않는다 (research R2)
- [X] T003 [P] 애플리케이션 진입점 `src/main/java/com/oneblog/OneBlogApplication.java`를 만들고 `@EnableAsync`를 붙인다
- [X] T004 [P] `src/main/resources/application.yml`을 만든다. 비밀값은 모두 환경변수 참조로만: `spring.datasource.url=jdbc:mysql://localhost:3306/one_blog?serverTimezone=Asia/Seoul&characterEncoding=UTF-8`, `username=${DB_USERNAME}`, `password=${DB_PASSWORD}`; `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`; `spring.flyway.enabled=true`; `spring.mail.host=smtp.gmail.com`, `port=587`, `username=${MAIL_USERNAME:}`, `password=${MAIL_PASSWORD:}`, `properties.mail.smtp.auth=true`, `properties.mail.smtp.starttls.enable=true`; `app.jwt.secret=${JWT_SECRET}`, `app.verification.code-pepper=${CODE_PEPPER}`, `app.mail.mode=${MAIL_MODE:smtp}`, `app.cookie.secure=${COOKIE_SECURE:true}`. 저장소 루트의 `.env`를 읽도록 `spring.config.import=optional:file:.env[.properties]`를 둔다. 비밀번호·키 원문을 파일에 쓰지 않는다 (constitution III)
- [X] T005 [P] 로컬 설정 예시 `src/main/resources/application-local.yml.example`을 만든다: `app.mail.mode: log`, `app.cookie.secure: false`, `logging.level.com.oneblog: DEBUG`. 실제 `application-local.yml`은 `.gitignore`에 이미 있으므로 커밋하지 않는다
- [X] T006a [P] 저장소 루트에 `.env.example`을 만든다(커밋 대상, 값은 비움): `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `CODE_PEPPER`, `MAIL_MODE`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `COOKIE_SECURE`와 각 설명·생성 방법. 실제 `.env`는 `.gitignore`로 제외된다
- [X] T006 [P] 테스트 설정 `src/test/resources/application-test.yml`을 만든다: datasource URL을 `jdbc:mysql://localhost:3306/one_blog_test?...`로, `spring.flyway.clean-disabled=false`, `app.mail.mode=log`, `app.jwt.secret`과 `app.verification.code-pepper`는 테스트 전용 고정 문자열(32바이트 이상), `app.cookie.secure=false`

**Checkpoint**: `./gradlew build -x test`가 성공한다. (`.env`가 있어야 앱이 기동된다)

---

## Phase 2: Foundational (모든 스토리의 전제)

**Purpose**: DB 스키마, 공통 보안·오류 처리, 화면 공통 스크립트. 이 단계가 끝나야 스토리 작업을 시작할 수 있다.

### 마이그레이션 (constitution IV, data-model.md)

- [X] T007 `src/main/resources/db/migration/V1__create_member_auth_tables.sql`을 만든다. Crowfoot ERD "One Blog 메인블로그"의 DDL에서 `users`, `verification_codes`, `refresh_tokens` 3개 테이블의 CREATE TABLE(컬럼·COMMENT·CHECK 제약 그대로), `fk_refresh_tokens_users`(ON DELETE CASCADE), 인덱스 `idx_users_name_phone`, `idx_verification_codes_email_purpose_created_at`, `idx_verification_codes_expires_at`, `idx_refresh_tokens_expires_at`만 담는다. 각 CREATE TABLE에 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci`를 붙인다. 다른 테이블은 넣지 않는다

### 설정 속성과 공통 빈

- [X] T008 [P] 설정 속성 레코드를 만든다: `src/main/java/com/oneblog/common/config/AppProperties.java` (`app.jwt.secret`, `app.verification.code-pepper`, `app.mail.mode`(smtp|log), `app.cookie.secure`)를 `@ConfigurationProperties(prefix = "app")`로 묶고, 시작할 때 `jwt.secret`이 32바이트 미만이면 실패하게 검증한다
- [X] T009 [P] `src/main/java/com/oneblog/common/config/PasswordConfig.java`에 `BCryptPasswordEncoder`(강도 10) 빈을 만든다 (SEC-01, research R7)
- [X] T010 [P] `src/main/java/com/oneblog/common/config/JwtConfig.java`에 `app.jwt.secret`으로 HS256 `JwtEncoder`(Nimbus)와 `JwtDecoder`(`NimbusJwtDecoder.withSecretKey`, MacAlgorithm.HS256) 빈을 만든다 (research R2)
- [X] T011 [P] `src/main/java/com/oneblog/common/config/AsyncConfig.java`에 메일 발송용 `ThreadPoolTaskExecutor`(core 2, max 4, queue 100)를 만든다 (research R5)

### 공통 오류 응답

- [X] T012 [P] `src/main/java/com/oneblog/common/web/ErrorResponse.java`(필드 `code`, `message`, `fieldErrors` 목록, 선택 필드 `remainingAttempts`, `retryAfterSeconds`)와 `src/main/java/com/oneblog/common/web/ApiException.java`(HTTP 상태, code, message, 추가 값)를 만든다. 형식은 contracts/auth-api.md "공통 오류 형식"
- [X] T013 `src/main/java/com/oneblog/common/web/GlobalExceptionHandler.java`(`@RestControllerAdvice`)를 만든다: `ApiException` → 지정 상태와 본문, `MethodArgumentNotValidException` → `400 VALIDATION_FAILED`와 `fieldErrors`, 그 밖의 예외 → `500 INTERNAL_ERROR`(내부 메시지를 응답에 노출하지 않음)

### 엔티티 (data-model.md, ERD 컬럼 그대로)

- [X] T014 [P] `src/main/java/com/oneblog/member/User.java` JPA 엔티티를 `users` 테이블에 매핑한다. 모든 컬럼(`id`, `email` VARCHAR(255) NULL, `login_id` VARCHAR(30) NULL, `password_hash` VARCHAR(100) NOT NULL, `name` VARCHAR(50), `nickname` VARCHAR(12), `phone` VARCHAR(20), `profile_image_url`, `bio`, `role` 기본 'USER', `status` 기본 'ACTIVE', `failed_login_count`, `locked_until`, `terms_agreed_at`, `privacy_agreed_at`, `notification_retention_days` 기본 30, `withdrawn_at`, `created_at`, `updated_at`, `deleted_at`)을 매핑하고 `ddl-auto=validate`를 통과해야 한다. `role`, `status`는 문자열 enum(`UserRole` USER/ADMIN, `UserStatus` ACTIVE/WITHDRAWN). 가입용 정적 팩토리 `createMember(email, passwordHash, name, nickname, phone, agreedAt)`를 둔다
- [X] T015 [P] `src/main/java/com/oneblog/member/UserRepository.java`: `findByEmail(String)`, `existsByEmail(String)`, `existsByNickname(String)`
- [X] T016 [P] `src/main/java/com/oneblog/verification/VerificationCode.java` 엔티티를 `verification_codes`에 매핑한다(`email` VARCHAR(255) NOT NULL, `purpose` VARCHAR(20) 'SIGNUP'|'PASSWORD_RESET', `code_hash` VARCHAR(100), `fail_count` TINYINT 기본 0, `expires_at`, `verified_at`, `used_at`, `created_at`). 메서드: `isExpired(now)`, `isInvalidated()`(fail_count ≥ 5), `recordFailure()`, `markVerified(now)`, `markUsed(now)`
- [X] T017 [P] `src/main/java/com/oneblog/verification/VerificationCodeRepository.java`: `findTopByEmailAndPurposeOrderByCreatedAtDesc`, `deleteByEmailAndPurpose`
- [X] T018 [P] `src/main/java/com/oneblog/auth/RefreshToken.java` 엔티티를 `refresh_tokens`에 매핑한다(`user_id` → `User` ManyToOne LAZY, `token_hash` CHAR(64) UNIQUE, `remember_me`, `last_activity_at`, `expires_at`, `revoked_at`, `created_at`). 메서드: `isActive(now)`(revoked_at NULL이고 expires_at > now), `touch(now)`(미체크일 때만 last_activity_at=now, expires_at=now+30분), `revoke(now)`
- [X] T019 [P] `src/main/java/com/oneblog/auth/RefreshTokenRepository.java`: `findByTokenHash(String)`

### 보안 공통 (SEC-04, SEC-10, research R3·R4)

- [X] T020 [P] `src/main/java/com/oneblog/common/security/CookieNames.java`(상수 `ACCESS_TOKEN`, `REFRESH_TOKEN`, `SIGNUP_TICKET`)와 `src/main/java/com/oneblog/common/security/AuthCookies.java`를 만든다. `ResponseCookie`로 쿠키를 만들고 지우는 메서드: Access(Path=/), Refresh(Path=/api/auth), 가입 티켓(Path=/api/auth/signup, SameSite=Strict, Max-Age 30분). 공통으로 HttpOnly, SameSite=Lax(티켓 제외), `app.cookie.secure` 값으로 Secure. `rememberMe`가 true면 Max-Age 14일, false면 Max-Age 없음(세션 쿠키)
- [X] T021 [P] `src/main/java/com/oneblog/common/security/TokenHasher.java`: 문자열의 SHA-256 16진수(64자)와, `code-pepper`를 키로 한 HMAC-SHA256 16진수를 만드는 유틸. 비교는 `MessageDigest.isEqual`로 상수 시간 비교
- [X] T022 `src/main/java/com/oneblog/auth/AccessTokenService.java`: `JwtEncoder`로 Access Token 발급(유효 10분, 클레임 `sub`=회원 ID, `sid`=refresh_tokens.id), 가입 티켓 발급(유효 30분, 클레임 `email`, `vid`=verification_codes.id, `typ`="SIGNUP"), 티켓 검증(서명·만료·`typ`)을 담당한다 (research R3, R5)
- [X] T023 `src/main/java/com/oneblog/common/security/CookieBearerTokenResolver.java`: `ACCESS_TOKEN` 쿠키 값을 돌려준다. `/api/auth/**`와 `/api/` 밖의 경로에서는 읽지 않는다(만료 쿠키가 로그인·refresh를 막지 않게). Authorization 헤더는 쓰지 않는다
- [X] T024 `src/main/java/com/oneblog/common/security/JwtCookieAuthenticationFilter.java`와 `src/main/java/com/oneblog/auth/SessionService.java`를 만든다. 쿠키의 JWT를 `JwtDecoder`로 검증하고(만료면 `401 TOKEN_EXPIRED`), `typ`=ACCESS인지 확인한 뒤 `sid`로 `RefreshToken`, `sub`로 `User`를 읽어 `RefreshToken.isActive(now)`가 false이거나 사용자가 `ACTIVE`가 아니면 `401 UNAUTHENTICATED`를 쓰고 중단한다. `X-User-Activity: 1` 헤더가 있고 `remember_me=false`이며 `last_activity_at`이 1분 이상 지났으면 `touch(now)`로 갱신한다 (contracts "인증 필터 동작", D-62, constitution III)
- [X] T025 `src/main/java/com/oneblog/common/config/SecurityConfig.java`를 만든다: 세션 STATELESS, `csrf(csrf -> csrf.spa())`, `oauth2ResourceServer`는 쓰지 않는다(쿠키 인증 요청의 CSRF 검사가 꺼지므로, research R2). 인증 실패는 `401 UNAUTHENTICATED`(ErrorResponse JSON)로 응답하는 `AuthenticationEntryPoint`. 허용 경로: 정적 파일(`/`, `/*.html`, `/css/**`, `/js/**`), `POST /api/auth/signup/**`, `GET /api/auth/signup/nickname-availability`, `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`. 그 밖의 `/api/**`는 인증 필요. `JwtCookieAuthenticationFilter`와 XSRF 쿠키를 미리 내려주는 필터를 `CsrfFilter` 뒤에 등록. 폼 로그인·HTTP Basic은 끈다
- [X] T026 [P] 메일 발송 계층을 만든다: `src/main/java/com/oneblog/mail/MailService.java`(인터페이스: `sendSignupCode(email, code)`, `sendAlreadyRegistered(email)`), `src/main/java/com/oneblog/mail/SmtpMailService.java`(`JavaMailSender`, `@Async`, `app.mail.mode=smtp`일 때 활성), `src/main/java/com/oneblog/mail/LogMailService.java`(`app.mail.mode=log`일 때 활성, 인증번호를 로그에 남김). 메일 본문은 한국어 평문. 발송 실패는 로그만 남기고 요청 응답에 영향을 주지 않는다 (research R5, R6)

### 화면 공통 (D-66, SEC-06, SEC-10)

- [X] T027 [P] `src/main/resources/static/js/api.js`를 만든다: `fetch`를 감싸 ① 모든 POST/PUT/DELETE에 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 붙이고, ② 호출 옵션 `userAction: true`일 때만 `X-User-Activity: 1`을 붙이고, ③ `401 TOKEN_EXPIRED`면 `POST /api/auth/refresh`를 한 번 호출한 뒤 원래 요청을 다시 보내고, ④ refresh가 실패하면 `/login.html`로 이동한다. JSON 오류 본문을 그대로 돌려준다
- [X] T028 [P] `src/main/resources/static/css/app.css`에 공통 스타일(반응형, 폼, 오류 문구, 상단 메뉴)을 만든다 (4.2 PC·모바일)
- [X] T029 [P] `src/main/resources/static/js/header.js`를 만든다: 페이지 로드 때 `GET /api/me`(`userAction: true` — 페이지 이동은 직접 한 행동, D-62)로 로그인 여부를 확인해 상단 메뉴에 닉네임과 로그아웃 버튼, 또는 로그인·회원가입 링크를 보여준다. 닉네임은 `textContent`로만 넣는다 (SEC-06)

**Checkpoint**: 앱이 기동되고 Flyway가 V1을 적용하며 `ddl-auto=validate`를 통과한다. 로그인 없이 `GET /api/me`는 `401 UNAUTHENTICATED`.

---

## Phase 3: User Story 1 - 이메일 인증으로 회원가입하기 (Priority: P1) 🎯 MVP

**Goal**: ① 이메일 인증 → ② 비밀번호 → ③ 이름·닉네임·전화번호·동의 세 단계로 계정을 만든다 (USR-01, USR-02, SEC-01, SEC-02, 4.6, 6.5).

**Independent Test**: quickstart 시나리오 1~6. 새 이메일로 끝까지 가입하면 `users`에 행이 생기고, 같은 이메일·닉네임으로는 다시 가입할 수 없다.

### Tests for User Story 1

- [X] T030 [P] [US1] `src/test/java/com/oneblog/verification/EmailVerificationIntegrationTest.java`: 인증번호 발송 202, 1분 안 재발송 `429 RESEND_TOO_SOON`, 틀린 번호 `400 CODE_MISMATCH`와 `remainingAttempts` 감소, 5번째 오답 뒤 `410 CODE_EXPIRED`, 10분 지난 번호(행의 expires_at을 과거로 바꿔서) `410 CODE_EXPIRED`, 가입된 이메일과 새 이메일의 응답 상태·본문이 같은지(D-28)를 확인한다. `@ActiveProfiles("test")`, 메일은 `LogMailService` 또는 가짜 `MailService` 빈으로 보낸 내용을 확인
- [X] T031 [P] [US1] `src/test/java/com/oneblog/member/SignupIntegrationTest.java`: 정상 가입 201과 `password_hash`가 bcrypt 형식, 티켓 없이 가입 `401 SIGNUP_TICKET_INVALID`(권한 거부 테스트), 티켓 재사용 `401`, 닉네임 중복·예약어(`Admin` 포함) `409 NICKNAME_UNAVAILABLE`, 비밀번호 규칙·불일치·동의 누락 `400 VALIDATION_FAILED`, 이미 가입된 이메일로 인증 후 가입 시 `409 SIGNUP_FAILED`를 확인한다

### Implementation for User Story 1

- [X] T032 [P] [US1] `src/main/java/com/oneblog/member/SignupPolicy.java`: 입력 정규화와 규칙 검사. 이메일 소문자·trim, 비밀번호 "8~15자, 영문·숫자·특수문자 각각 1자 이상"(SEC-02), 닉네임 "`^[가-힣A-Za-z0-9]{2,12}$`"와 예약어 "`관리자`, `admin`, `운영자`, `탈퇴한 회원`"(대소문자 무시), 이름 "앞뒤 공백 제거 후 1~50자", 전화번호 "하이픈·공백 제거 후 `^01[0-9]{8,9}$`"
- [X] T033 [P] [US1] 요청·응답 DTO를 만든다: `src/main/java/com/oneblog/verification/dto/EmailCodeRequest.java`(`@Email`, 최대 255자), `VerifyCodeRequest.java`(email, code `^[0-9]{6}$`), `src/main/java/com/oneblog/member/dto/SignupRequest.java`(password, passwordConfirm, name, nickname, phone, agreeTerms, agreePrivacy — Bean Validation으로 필수값과 `@AssertTrue` 동의), `NicknameAvailabilityResponse.java`(available, reason: TAKEN|RESERVED|INVALID_FORMAT)
- [X] T034 [US1] `src/main/java/com/oneblog/verification/EmailVerificationService.java`를 만든다: `sendSignupCode(email)` — 같은 이메일·`SIGNUP`의 최근 행이 1분 안이면 `429 RESEND_TOO_SOON`(retryAfterSeconds), 아니면 이전 행 삭제 후 `SecureRandom` 6자리를 HMAC 해시해 새 행 저장(expires_at = now+10분), 가입된 이메일이면 `MailService.sendAlreadyRegistered`, 아니면 `sendSignupCode`. 응답은 두 경우 같음. `verify(email, code)` — 최근 행이 없거나 만료·무효면 `410 CODE_EXPIRED`, 틀리면 `recordFailure` 후 `400 CODE_MISMATCH`(remainingAttempts = 5 − fail_count, 0이면 410), 맞으면 `markVerified`하고 가입 티켓 발급 (USR-02, D-28, research R5)
- [X] T035 [US1] `src/main/java/com/oneblog/member/SignupService.java`를 만든다: `signup(ticket, request)` — 티켓 검증 실패 시 `401 SIGNUP_TICKET_INVALID`; 티켓의 `vid` 행이 없거나 `verified_at` NULL, `used_at` 있음, 이메일 불일치면 401; `SignupPolicy`로 검증; 비밀번호 불일치 `400`; 닉네임 중복·예약어 `409 NICKNAME_UNAVAILABLE`; 이메일이 이미 가입됐으면 `409 SIGNUP_FAILED`; 통과하면 bcrypt 해시로 `User.createMember` 저장(동의 시각 = now)하고 인증 행 `markUsed`. 동시 가입으로 유니크 제약이 깨지면(`DataIntegrityViolationException`) 닉네임이면 409 NICKNAME_UNAVAILABLE, 이메일이면 409 SIGNUP_FAILED로 바꾼다. 한 트랜잭션으로 처리 (USR-01, FR-001~006)
- [X] T036 [US1] `src/main/java/com/oneblog/member/NicknameService.java`: `check(nickname)` — 형식 오류 INVALID_FORMAT, 예약어 RESERVED, 사용 중 TAKEN, 아니면 available
- [X] T037 [US1] `src/main/java/com/oneblog/member/SignupController.java`를 만든다: `POST /api/auth/signup/email-code`(202, body `message`, `expiresInSeconds` 600, `resendAvailableInSeconds` 60), `POST /api/auth/signup/email-code/verify`(200, `SIGNUP_TICKET` 쿠키 설정, body `verified`, `signupExpiresInSeconds` 1800), `GET /api/auth/signup/nickname-availability`, `POST /api/auth/signup`(201, 티켓 쿠키 삭제, body `message`). 형식은 contracts/auth-api.md 그대로
- [X] T038 [US1] `src/main/resources/static/signup.html`과 `src/main/resources/static/js/signup.js`를 만든다: 한 페이지에서 ① 이메일 입력·인증번호 받기·다시 보내기(남은 초 카운트다운)·번호 입력 ② 비밀번호 두 번(규칙 안내와 화면 검사) ③ 이름·닉네임(입력 후 중복 확인)·전화번호·[필수] 이용약관·[필수] 개인정보 동의 체크박스와 가입하기 순서로 단계를 바꿔 보여준다. 오류 문구는 서버 `message`를 `textContent`로 표시, 성공하면 `/login.html?signup=done`으로 이동. 모든 버튼 클릭 요청은 `api.js`의 `userAction: true`로 보낸다

**Checkpoint**: quickstart 1~6이 통과한다. US1만으로 계정 생성을 시연할 수 있다.

---

## Phase 4: User Story 2 - 이메일·비밀번호로 로그인하고 로그아웃하기 (Priority: P2)

**Goal**: 이메일·비밀번호 로그인, 모든 블로그에서 유지되는 로그인 상태, 즉시 로그아웃 (USR-03, USR-04, SEC-04).

**Independent Test**: quickstart 시나리오 7~9, 12, 14. 로그인 후 상단에 닉네임이 보이고, 로그아웃 후 이전 토큰으로 `/api/me`가 401이다.

### Tests for User Story 2

- [X] T039 [P] [US2] `src/test/java/com/oneblog/auth/LoginLogoutIntegrationTest.java`: 대소문자 다른 이메일로 로그인 성공과 쿠키 2개(HttpOnly), 비밀번호 틀림과 없는 이메일이 같은 `401 LOGIN_FAILED`·같은 문구, 로그인 없이 `/api/me` `401 UNAUTHENTICATED`(권한 거부 테스트), 로그아웃 뒤 이전 `ACCESS_TOKEN`으로 `/api/me` `401 UNAUTHENTICATED`(SC-005), CSRF 헤더 없는 `POST /api/auth/login` 403, 만료된 Access Token에 `401 TOKEN_EXPIRED` 후 `/api/auth/refresh`로 다시 받기를 확인한다

### Implementation for User Story 2

- [X] T040 [P] [US2] DTO를 만든다: `src/main/java/com/oneblog/auth/dto/LoginRequest.java`(email, password, rememberMe 기본 false), `LoginResponse.java`(nickname), `MeResponse.java`(id, nickname, role)
- [X] T041 [US2] `src/main/java/com/oneblog/auth/AuthService.java`를 만든다: `login(request)` — 이메일 정규화 후 조회, 없거나 `ACTIVE`가 아니거나 비밀번호 불일치면 모두 `401 LOGIN_FAILED`("이메일 또는 비밀번호가 올바르지 않습니다."). 사용자가 없을 때도 더미 해시로 `matches`를 실행해 응답 시간 차이를 줄인다. 성공하면 무작위 256비트 Refresh Token을 만들어 SHA-256 해시로 `refresh_tokens`에 저장(remember_me, last_activity_at=now, expires_at = 체크 시 now+14일, 미체크 시 now+30분)하고 Access Token 발급. `refresh(rawRefreshToken)` — 해시로 행 조회, 없거나 `isActive` false거나 회원 비활성이면 `401 SESSION_EXPIRED`, 아니면 새 Access Token 발급(Refresh Token 교체 없음). `logout(rawRefreshToken)` — 행이 있으면 `revoke(now)` (USR-03, USR-04, research R3)
- [X] T042 [US2] `src/main/java/com/oneblog/auth/AuthController.java`를 만든다: `POST /api/auth/login`(200, `ACCESS_TOKEN`·`REFRESH_TOKEN` 쿠키, rememberMe에 따라 세션/14일), `POST /api/auth/refresh`(204, 새 `ACCESS_TOKEN`; 실패 시 두 쿠키 삭제 후 401 SESSION_EXPIRED), `POST /api/auth/logout`(204, 두 쿠키 삭제, 이미 로그아웃이어도 204), `GET /api/me`(200 MeResponse). 형식은 contracts/auth-api.md 그대로
- [X] T043 [P] [US2] `src/main/resources/static/login.html`과 `src/main/resources/static/js/login.js`를 만든다: 이메일, 비밀번호, "로그인 유지" 체크박스, 로그인 버튼, 회원가입 링크. `?signup=done`이면 "가입이 완료되었습니다. 로그인해 주세요." 표시. 성공 시 `/`로 이동, 실패 시 서버 문구를 `textContent`로 표시
- [X] T044 [P] [US2] `src/main/resources/static/index.html`을 만든다: 상단 메뉴(`header.js`)와 "메인블로그" 자리 표시 내용. 로그아웃 버튼은 `POST /api/auth/logout`(userAction) 뒤 `/`로 이동

**Checkpoint**: quickstart 7~9, 12, 14가 통과한다. US1과 함께 가입부터 로그인·로그아웃까지 시연할 수 있다.

---

## Phase 5: User Story 3 - "로그인 유지" 선택과 자동 로그아웃 (Priority: P3)

**Goal**: 미체크 시 브라우저 종료·30분 무활동 로그아웃, 체크 시 14일 유지 (SEC-04, D-61, D-62).

**Independent Test**: quickstart 시나리오 10, 11. 미체크 로그인 후 `expires_at`이 지나면 다음 요청이 401이고, 체크 로그인은 `expires_at`이 로그인 + 14일이다.

### Tests for User Story 3

- [X] T045 [P] [US3] `src/test/java/com/oneblog/auth/RememberMeIntegrationTest.java`: 미체크 로그인의 쿠키에 Max-Age가 없고 체크 로그인은 Max-Age 14일, 미체크 행의 `expires_at`을 과거로 바꾸면 `/api/me` 401과 refresh `401 SESSION_EXPIRED`, `X-User-Activity: 1` 요청은 (마지막 갱신 1분 경과 시) `expires_at`을 now+30분으로 미루고 헤더 없는 요청은 미루지 않음, 체크 로그인은 활동 헤더가 있어도 `expires_at`이 바뀌지 않음을 확인한다

### Implementation for User Story 3

- [X] T046 [US3] `JwtCookieAuthenticationFilter`·`SessionService`(T024)와 `AuthService.login`(T041)의 로그인 유지 동작이 contracts "인증 필터 동작"과 data-model의 refresh_tokens 상태 전이(체크: 로그인 + 14일 고정, 미체크: last_activity_at + 30분)와 일치하는지 맞추고, 1분 간격 갱신이 같은 요청에서 두 번 쓰지 않도록 `src/main/java/com/oneblog/auth/RefreshToken.java`의 `touch`로 정리한다
- [X] T047 [US3] `signup.js`, `login.js`, `header.js`의 모든 `api.js` 호출이 페이지 로드·버튼·폼 제출일 때만 `userAction: true`를 쓰는지 점검한다. 타이머 등 저절로 보내는 요청(예: 다시 보내기 카운트다운)은 서버를 호출하지 않거나 `userAction` 없이 보낸다 (D-62). 파일: `src/main/resources/static/js/`

**Checkpoint**: quickstart 10, 11이 통과한다. 세 스토리가 모두 독립적으로 동작한다.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T048 [P] `readme.md`의 "기술 스택" 줄을 "Spring Boot 4.1 · Java 21 · Spring Security · MySQL 8.4 · Flyway · 정적 HTML + JS · Toast UI Editor"로 고치고, "로컬 실행" 절에 quickstart.md 링크를 단다
- [X] T049 [P] 보안 기준선 점검(constitution III): 코드 전체에서 `innerHTML` 사용이 없는지, 문자열로 만든 SQL이 없는지, 비밀값 원문이 저장소에 없는지(`git grep -nE "(JWT_SECRET|MAIL_PASSWORD|DB_PASSWORD)\s*[:=]\s*[^$]"`)를 확인하고 결과를 `specs/001-signup-login/checklists/security.md`에 기록한다
- [ ] T050 `./gradlew test`를 `one_blog_test` DB로 실행해 모두 통과시키고, quickstart.md 시나리오 1~14를 로컬에서 수동으로 확인한다
- [ ] T051 `docs/roadmap.md`의 001 상태를 "완료"로 바꾸고, main에 `v0.1.0` 태그를 붙인다(태그 메시지: "001 회원가입·로그인·로그아웃 — USR-01~04, SEC-01, SEC-02, SEC-04") (constitution 개발 흐름)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 의존 없음
- **Foundational (Phase 2)**: Setup 완료 후. 모든 스토리를 막는다
- **US1 (Phase 3)**: Foundational 완료 후
- **US2 (Phase 4)**: Foundational 완료 후. 시연에는 US1로 만든 계정이 필요하지만, 테스트는 리포지토리로 회원을 직접 넣어 독립적으로 할 수 있다
- **US3 (Phase 5)**: US2(로그인·필터)에 의존
- **Polish (Phase 6)**: 모든 스토리 완료 후

### 작업 사이 의존

- T007(마이그레이션) → T014~T019(엔티티, validate 통과)
- T008 → T010, T020, T026
- T010 → T022 → T023, T024 → T025
- T012 → T013
- T032, T033 → T034, T035, T036 → T037 → T038
- T040 → T041 → T042 → T043, T044
- T024, T041 → T046; T027 → T047

### Parallel Opportunities

- Phase 1: T003, T004, T005, T006
- Phase 2: T008~T012, T014~T021, T026~T029 (서로 다른 파일)
- US1: 테스트 T030, T031과 T032, T033
- US2: T039, T040, 그리고 T042 뒤의 T043, T044
- US1의 화면(T038)과 US2의 서비스(T041)는 Foundational 뒤라면 동시에 진행할 수 있다

---

## Parallel Example: User Story 1

```bash
# 테스트와 규칙·DTO를 먼저 동시에:
Task: "T030 EmailVerificationIntegrationTest in src/test/java/com/oneblog/verification/"
Task: "T031 SignupIntegrationTest in src/test/java/com/oneblog/member/"
Task: "T032 SignupPolicy in src/main/java/com/oneblog/member/SignupPolicy.java"
Task: "T033 DTOs in src/main/java/com/oneblog/{verification,member}/dto/"
```

---

## Implementation Strategy

### MVP First (User Story 1만)

1. Phase 1 Setup → Phase 2 Foundational
2. Phase 3 US1
3. **멈추고 확인**: quickstart 1~6 (가입만 되는 상태)

### Incremental Delivery

1. Setup + Foundational → 기반 완성
2. US1 → 가입 시연 가능
3. US2 → 가입·로그인·로그아웃 시연 가능 (사실상 001의 완성 기준)
4. US3 → 로그인 유지 규칙 완성 → Polish → `v0.1.0`

### 하루 일정 기준

2주에 15개 기능이므로 001은 하루 안팎이 목표다. 001은 프로젝트 뼈대(Phase 1·2)를 함께 만들어 다른 기능보다 길다. 밀리면 US3(로그인 유지)를 먼저 끝내지 못해도 US1·US2까지로 `v0.1.0`을 내고 US3를 `v0.1.1`로 낼 수 있다.

---

## Notes

- [P] 작업 = 다른 파일, 의존 없음
- [Story] 라벨로 작업과 스토리를 연결한다
- 작업 하나 또는 논리적 묶음마다 커밋하고, 커밋 메시지에 요구사항 ID를 적는다 (constitution 개발 흐름)
- 테이블·컬럼을 바꿔야 하면 Crowfoot ERD를 먼저 고치고 새 마이그레이션으로 반영한다. V1을 적용한 뒤에는 V1 파일을 고치지 않는다 (constitution IV)
