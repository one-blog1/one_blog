# Feature Specification: 소셜 로그인 (카카오·네이버·구글)

**Feature Branch**: `main` | **Created**: 2026-10-12 | **Status**: Draft

**Input**: "소셜 로그인 기능까지 구현", "카카오 네이버 구글", 카카오 이메일은 우리 이메일 인증으로(안 A), "네이버 등록했어 기능 만들어줘", "카카오도 등록했어"

**관련 요구사항**: USR-09(새로 정함), USR-01·USR-02(가입·이메일 인증), USR-03(로그인), USR-05(탈퇴), USR-06(비밀번호 찾기), USR-07(재확인), SEC-03·SEC-13(잠금·사람 확인), SEC-04(쿠키), SCL-01(서버 메모리에 상태 두지 않음), 4.6(처리방침), D-126(새로 정함)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 소셜 계정으로 처음 가입 (Priority: P1)

1. **Given** 로그인·가입 화면, **When** "카카오로 로그인" 등을 누르고 그 사이트에서 동의하면, **Then** 처음이면 소셜 가입 화면으로 온다.
2. 구글처럼 사이트가 인증한 이메일이 있으면 그 이메일로, 없으면(카카오 일반 앱, 네이버) 이메일을 넣고 우리 인증번호로 확인한다.
3. 이름·닉네임(사이트 값으로 미리 채움)·휴대전화·약관 동의를 하면 가입되고 바로 로그인된다. 비밀번호는 만들지 않는다.

### User Story 2 - 다음부터 바로 로그인 (P1)

연결된 소셜 계정으로 오면 바로 로그인되어 첫 화면으로 간다 (로그인 유지는 체크하지 않은 것과 같다).

### User Story 3 - 기존 회원 연결 (P1)

1. 같은 이메일의 회원이 있어도 저절로 붙이지 않는다. 가입 화면 아래 "이미 회원이신가요?"에서 비밀번호로 로그인하면 연결된다.
2. 로그인한 회원은 내 정보 > 소셜 로그인에서 사이트별로 연결하기·연결 끊기를 한다.

### Edge Cases

- 비밀번호 없는 회원: 비밀번호 로그인 불가, 프로필 수정 전 재확인·탈퇴는 "비밀번호 찾기로 먼저 만들어 주세요"(PASSWORD_NOT_SET), 마지막 연결은 끊을 수 없다(LAST_LOGIN_METHOD).
- 다른 회원에 이미 연결된 소셜 계정은 연결할 수 없다. 한 회원은 사이트마다 하나만 연결한다.
- 탈퇴하면 연결을 지워 같은 소셜 계정으로 다시 가입할 수 있다.
- 동의 화면에서 취소하거나 state가 맞지 않으면 로그인 화면(연결 중이면 내 정보)에 문구를 보인다.
- 키가 없는 사이트는 단추가 보이지 않고 /oauth2/authorization/{사이트}도 동작하지 않는다.
- 관리자 계정은 소셜 로그인을 쓸 수 없다.

## Requirements *(mandatory)*

- **FR-001**: Spring Security OAuth2 Client로 /oauth2/authorization/{kakao|naver|google} → /login/oauth2/code/{id}. 자동 설정 없이 키가 있는 사이트만 등록한다.
- **FR-002**: 세션을 쓰지 않는다. 사이트로 보내는 요청(state·PKCE)은 서명한 쿠키(OAUTH2_REQUEST, /login/oauth2, Lax, 10분), 가입 대기는 SOCIAL_PENDING(/api/auth, Lax, 15분), 연결 표시는 SOCIAL_LINK(/login/oauth2, Lax, 10분). 모두 HttpOnly, JWT 서명.
- **FR-003**: 사이트의 액세스 토큰은 저장하지 않는다(사용자 정보를 한 번 읽고 버린다).
- **FR-004**: social_accounts(provider, provider_user_id) 유일, (user_id, provider) 유일. V19.
- **FR-005**: 비밀번호 없는 회원의 password_hash는 "!SOCIAL"(bcrypt가 아니라 어떤 비밀번호와도 맞지 않음). 비밀번호 찾기로 만들 수 있다.
- **FR-006**: API: GET /api/auth/social/providers, GET /api/auth/social/pending, POST /api/auth/signup/social, POST /api/auth/social/link, POST /api/auth/social/cancel, GET /api/me/social, POST /api/me/social/{p}/link, DELETE /api/me/social/{p}.
- **FR-007**: 키는 KAKAO_/NAVER_/GOOGLE_CLIENT_ID·_CLIENT_SECRET 환경변수. 돌아오는 주소는 PUBLIC_BASE_URL(없으면 요청 주소) + /login/oauth2/code/{id}.
- **FR-008**: 처리방침에 소셜 로그인 항목·보관 기간·쿠키를 더한다.
