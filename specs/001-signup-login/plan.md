# Implementation Plan: 회원가입·로그인·로그아웃

**Branch**: `main` (constitution: main에 바로 올림) | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-signup-login/spec.md`

## Summary

이메일 인증(6자리 번호) → 비밀번호 → 이름·닉네임·전화번호·동의 순서의 회원가입과, 이메일·비밀번호 로그인, "로그인 유지", 로그아웃을 만든다. 프로젝트의 첫 기능이라 Spring Boot 프로젝트 뼈대, Flyway V1 마이그레이션(`users`, `verification_codes`, `refresh_tokens`), Spring Security 설정(JWT 쿠키 인증, CSRF, 보안 기준선), 공통 화면 스크립트(`api.js`)도 이 기능에서 함께 만든다. 로그인 상태는 JWT Access Token(10분)과 DB에 저장한 Refresh Token으로 유지하고, 매 요청 DB의 로그인 행을 확인해 로그아웃과 30분 무활동을 즉시 반영한다([research.md](research.md) R3).

## Technical Context

**Language/Version**: Java 21 (Temurin)

**Primary Dependencies**: Spring Boot 4.1.x — `spring-boot-starter-webmvc`, `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`(JWT 인코딩·검증), `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-mail`, `spring-boot-starter-flyway` + `flyway-mysql`, `mysql-connector-j`. 빌드는 Gradle Kotlin DSL + Wrapper.

**Storage**: MySQL 8.4 (utf8mb4). 스키마는 Flyway로만 바꾼다. 데이터 모델은 Crowfoot ERD를 따른다 ([data-model.md](data-model.md)).

**Testing**: JUnit 5, Spring Boot Test(`spring-boot-starter-test`, `spring-boot-starter-webmvc-test`, `spring-security-test`), 로컬 MySQL의 `one_blog_test` DB를 쓰는 통합 테스트 (R8)

**Target Platform**: 개발은 macOS(Apple Silicon), 운영은 Linux 서버 1대 (4.3)

**Project Type**: 웹 서비스 — 하나의 Spring Boot 애플리케이션이 REST API와 정적 HTML·JS 화면을 함께 제공 (D-66)

**Performance Goals**: 가입·로그인 화면과 API 응답 2초 안 (4.2, SC-003). 인증 메일 1분 안 도착 (SC-002)

**Constraints**: 서버 메모리에 상태 없음 (SCL-01). 비밀값은 환경변수로만 (constitution III). 가입 여부가 화면·응답·응답 시간으로 드러나지 않음 (D-28, SC-004)

**Scale/Scope**: 1차 서버 1대, 화면 3개(메인·가입·로그인), API 7개, 테이블 3개

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 확인 | 결과 |
|---|---|---|
| I. 요구사항 문서가 기준 | 스펙에 USR-01~04, SEC-01·02·04 등 ID 인용. 요구사항 문서 변경 없음 | ✅ |
| I. 보류 항목 구현 금지 | 6장 보류 항목(태그 금칙어)과 무관 | ✅ |
| II. 작은 단위로 끝까지 | 화면 → API → DB까지 회원가입·로그인 한 흐름. 1단계 순서의 첫 기능(USR-01~04) | ✅ |
| II. 미룬 항목 기록 | spec.md "후속 작업"에 SEC-03, SEC-13, D-80, D-98, USR-06·08 등 기록 | ✅ |
| III. bcrypt | `BCryptPasswordEncoder` (R7) | ✅ |
| III. 역할을 매 요청 DB 확인 | 인증 필터가 매 요청 users·refresh_tokens 행 확인 (R3). 블로그별 역할은 이 기능에 없음 | ✅ |
| III. SQL 파라미터 바인딩 | JPA·Spring Data만 사용, 문자열로 SQL을 만들지 않음 | ✅ |
| III. textContent | 화면은 모든 사용자 입력을 `textContent`로 표시. 글 본문 없음 | ✅ |
| III. 서버 마스킹 | 이 기능은 본인에게만 자기 정보를 보여 가릴 대상 없음 | ✅ (해당 없음) |
| III. 비밀값 저장소 밖 | DB·JWT·메일·HMAC 비밀값은 환경변수. `.gitignore`가 `application-local.yml`, `.env` 제외 | ✅ |
| IV. Flyway만, ddl-auto 금지 | `spring.jpa.hibernate.ddl-auto=validate`, 스키마는 V1 마이그레이션 | ✅ |
| IV. created_at·updated_at·deleted_at | `users`에 있음. 인증번호·토큰은 ERD대로 만료 시각으로 관리 | ✅ |
| IV. utf8mb4 | DB와 V1의 테이블 옵션 모두 utf8mb4 | ✅ |
| IV. 서버 메모리 상태 없음 | 인증번호·로그인 정보·활동 시각 모두 DB. 메일 비동기 발송 큐는 처리 중 작업일 뿐 상태 아님 | ✅ |
| IV. Crowfoot ERD 기준 | 3개 테이블을 ERD DDL에서 그대로 가져옴. ERD 변경 없음 | ✅ |
| IV. 쓸 테이블만 생성 | V1은 이 기능의 3개 테이블만. `account_lookup_tokens`, `shedlock` 등은 쓰는 기능에서 추가 | ✅ |
| IV. ShedLock | 이 기능에 `@Scheduled` 없음 (만료 행 정리는 D-83 후속) | ✅ (해당 없음) |
| V. 단순함 | 외부 JWT 라이브러리·Redis·Docker 없이 구현 (R2, R8) | ✅ |
| 개발 흐름: 권한 거부 테스트 | 로그인 안 한 요청·폐기된 토큰·티켓 없는 가입이 거부되는지 테스트 (quickstart 5, 9, 14) | ✅ |
| 개발 흐름: 릴리즈 태그 | 완료 시 `v0.1.0` | ✅ |

**Post-design 재확인 (Phase 1 후)**: data-model.md는 ERD 컬럼을 그대로 쓰고, contracts는 쿠키·CSRF 규칙을 SEC-04·SEC-10대로 정했다. 위반 없음.

## Project Structure

### Documentation (this feature)

```text
specs/001-signup-login/
├── spec.md
├── plan.md              # 이 파일
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   └── auth-api.md      # Phase 1
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks 에서 생성
```

### Source Code (repository root)

```text
build.gradle.kts
settings.gradle.kts
gradlew, gradlew.bat, gradle/wrapper/
src/
├── main/
│   ├── java/com/oneblog/
│   │   ├── OneBlogApplication.java
│   │   ├── common/
│   │   │   ├── config/          # SecurityConfig, JwtConfig, AsyncConfig, 설정 속성(@ConfigurationProperties)
│   │   │   ├── security/        # 쿠키 토큰 해석, 로그인 행 확인 필터, 쿠키 생성 도우미
│   │   │   └── web/             # 공통 오류 응답(ErrorResponse), @RestControllerAdvice
│   │   ├── member/              # 회원(users) — 엔티티, 리포지토리, 가입 서비스, 닉네임 확인, 컨트롤러
│   │   ├── verification/        # 이메일 인증번호 — 엔티티, 리포지토리, 발급·확인 서비스, 가입 티켓
│   │   ├── auth/                # 로그인·refresh·로그아웃·me — 서비스, 컨트롤러, refresh_tokens 엔티티
│   │   └── mail/                # MailSender 감싸기, smtp/log 모드, 메일 문구
│   └── resources/
│       ├── application.yml              # 공통 설정, 비밀값은 ${ENV}로만
│       ├── application-local.yml.example # 로컬 설정 예시 (실제 파일은 .gitignore)
│       ├── db/migration/
│       │   └── V1__create_member_auth_tables.sql
│       └── static/
│           ├── index.html
│           ├── signup.html
│           ├── login.html
│           ├── css/app.css
│           └── js/ (api.js, header.js, signup.js, login.js)
└── test/
    ├── java/com/oneblog/
    │   ├── member/        # 가입 검증 규칙 단위 테스트, 가입 API 통합 테스트
    │   ├── verification/  # 인증번호 만료·5회·재발송 테스트
    │   └── auth/          # 로그인·로그아웃·로그인 유지·권한 거부 통합 테스트
    └── resources/application-test.yml   # one_blog_test DB, 가짜 메일
```

**Structure Decision**: Spring Boot 애플리케이션 하나를 저장소 루트에 둔다. 화면은 정적 파일이라 별도 프론트엔드 프로젝트가 없다(D-66). 패키지는 기능 단위(`member`, `verification`, `auth`, `mail`)로 나누고 공통 보안·오류 처리는 `common`에 둔다. 다음 기능(블로그, 게시판)은 `blog`, `post`처럼 같은 수준에 패키지를 더한다.

## Complexity Tracking

constitution 위반이 없어 비워 둔다.
