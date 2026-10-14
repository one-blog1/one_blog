# Implementation Plan: 소셜 간편 가입 (가입 화면 한 장)

**Branch**: `feature/social-quick-signup` | **Date**: 2026-10-14 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/027-social-quick-signup/spec.md` (2026-10-14 확인)

## Summary

이미 있는 소셜 로그인(022, D-126)의 가입 화면을 고친다. 사이트에서 받은 이메일·이름·휴대전화·닉네임을 가입 대기 쿠키에 담고, 가입 화면은 **빈칸과 약관 동의만** 묻는다. 받은 이름·전화번호는 서버가 가려서 보여 주고, 가입할 때는 서버가 쿠키의 원래 값을 쓴다. 같은 이메일의 회원이 있으면 "이미 있음" 안내와 비밀번호 연결만 보여 준다.

DB·ERD·마이그레이션은 바꾸지 않는다. 바뀌는 것은 소셜 패키지 몇 파일, 소셜 가입 화면, 개인정보 처리방침, 요구사항 문서(D-139)다.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.1, Spring Security 7 (OAuth2 Client, 자동 설정 없이 `SocialClientRegistrations`)

**Primary Dependencies**: 기존 그대로. 새 라이브러리 없음

**Storage**: MySQL 8.4. 테이블 변경 없음 (`users`, `social_accounts` 그대로)

**Testing**: JUnit 5 + MockMvc (`SocialProfilesTest`, `SocialLoginIntegrationTest`에 더함). 이 세션은 Gradle이 막혀 있어 브랜치 push 후 CI로 확인

**Target Platform**: 운영 서버(블루-그린, 색마다 앱 2개). 상태는 서명한 쿠키에만 둔다 (SCL-01)

**Project Type**: 웹 서비스 + 정적 HTML/JS

**Performance Goals / Scale**: 해당 없음 (가입 화면 하나)

**Constraints**: CSP `img-src 'self'` (카카오 사진은 이번에 쓰지 않음), 가입 대기 쿠키 15분, 쿠키 크기 4KB 안 (값 7개라 1KB 안팎)

## Constitution Check

| 원칙 | 확인 |
|---|---|
| I. 요구사항 기준 | 스펙에 USR-01·USR-02·USR-09·4.6 인용. 결정은 D-139로 8장에 남기고 3장·4장·처리방침을 같은 PR에서 고침 (문서 저장소는 main에 바로) |
| II. 작은 단위 완성 | 화면 → API → 서비스 한 흐름. 카카오 사진은 "후속 작업"으로 남김 |
| III. 보안 기준선 | 이름·전화번호·이메일 가리기는 서버(`PendingView`)에서. 화면은 `textContent`만. 쿠키는 서명(HS256)·HttpOnly·15분. 같은 이메일 계정에 저절로 붙이지 않음 (비밀번호 확인 필수, 로그인 잠금·Turnstile 그대로) |
| IV. 구조 | DB 변경 없음 → ERD·Flyway 손대지 않음. 서버 메모리 상태 없음 |
| V. 단순함 | 새 API 없음. 기존 `/pending`, `/signup/social`, `/social/link`의 필드만 늘림 |

통과. 위반 없음.

## Project Structure

### Documentation (this feature)

```text
specs/027-social-quick-signup/
├── spec.md
├── plan.md          # 이 파일
├── research.md      # 결정과 이유
├── data-model.md    # 쿠키·화면 값 (DB 변경 없음)
├── quickstart.md    # 확인 방법
└── contracts/
    └── social-signup-api.md
```

### Source Code (바꿀 파일)

```text
src/main/java/com/oneblog/auth/social/
├── SocialClientRegistrations.java   # 카카오 scope에 account_email
├── SocialProfiles.java              # 네이버 mobile·이메일 믿음, 전화번호 정리
├── SocialTickets.java               # Pending에 phone 추가 (claim "phone")
├── SocialLoginService.java          # view(가리기·빈칸 표시·닉네임 자동), signup(쿠키 값 우선), linkWithPassword(이메일 생략 가능)
├── SocialNicknames.java             # 새 파일: 닉네임 정하기 (형식 맞추기, 겹치면 숫자)
└── SocialController.java            # 변경 거의 없음 (LinkRequest email 선택)

src/main/resources/static/
├── social-signup.html               # 받은 값 줄(가린 값), 빈칸만 입력, "이미 있음" 영역
├── js/pages/social-signup.js        # need* 값으로 칸 보이기/숨기기, 이미 있음이면 비밀번호만
└── privacy.html                     # 카카오 이메일, 네이버 이름·휴대전화

src/test/java/com/oneblog/auth/social/
├── SocialProfilesTest.java          # 사이트별 값 → Pending
├── SocialNicknamesTest.java         # 새 파일
└── SocialLoginIntegrationTest.java  # 빈칸·동의·이메일 중복·쿠키 값 우선

문서 저장소 (one-blog_docs, main)
├── docs/requirements/03-functional-requirements.md  # USR-01·USR-09
├── docs/requirements/04-non-functional-requirements.md  # 4.6 수집 항목
└── docs/requirements/08-decision-log.md  # D-139
```

**Structure Decision**: 기존 `auth/social` 패키지 안에서 고친다. 닉네임 정하기만 테스트하기 쉽게 작은 클래스로 뺀다.

## 흐름 (바뀐 뒤)

1. 로그인 화면에서 카카오·네이버·구글 → 사이트 동의 → `/login/oauth2/code/*`
2. `SocialLoginHandlers`: 연결된 회원이면 로그인(지금 그대로). 처음이면 가입 대기 쿠키 → `/social-signup.html`
3. 화면이 `GET /api/auth/social/pending`을 읽는다
   - `emailInUse=true` → "이미 가입한 계정이 있습니다" + 비밀번호 칸만
   - 아니면 받은 값(가린 값) 표시 + `needName`·`needPhone`·`needEmail`인 칸만 입력 + 닉네임(사이트 닉네임으로 미리 채움, 고칠 수 있음) + 약관 동의
4. `POST /api/auth/signup/social` → 서버가 쿠키의 이름·전화번호를 먼저 쓰고, 비어 있던 것만 요청 값을 쓴다 → 가입·로그인 → 첫 화면

## 후속 작업 (이번에 안 함)

- 카카오 프로필 사진을 우리 저장소로 받아 저장 (CSP 때문에)
- 같은 휴대전화 번호 중복 정책 (지금 규칙: 1차는 허용)

## Complexity Tracking

없음.
