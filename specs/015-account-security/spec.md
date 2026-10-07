# Feature Specification: 계정 보안 세부 (로그인 잠금·사람 확인·IP 제한·비밀번호/이메일 찾기·보안 헤더)

**Feature Branch**: `main` | **Created**: 2026-10-07 | **Status**: Draft

**Input**: "015 계정 보안 세부: 로그인 잠금·CAPTCHA·IP 제한·비밀번호/이메일 찾기 (SEC-03, 05, 13, USR-06, 08, D-80)". 보안 헤더(SEC-12)도 함께 마무리한다.

**관련 요구사항**: SEC-03, SEC-05, SEC-12, SEC-13, USR-06, USR-08, 4.3(프록시 헤더), 4.5(임시 토큰 삭제), 6.6(트래픽 제한), 6.7, D-26, D-79, D-80

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 로그인 잠금과 사람 확인 (Priority: P1)

1. **Given** 계정, **When** 비밀번호를 5번 연속 틀리면, **Then** 5분 동안 잠기고 남은 시간을 안내한다 (SEC-03). 잠긴 동안에는 맞는 비밀번호도 거부한다.
2. **Given** 3번 틀린 계정, **When** 다시 로그인하면, **Then** Cloudflare Turnstile 사람 확인을 거쳐야 한다 (SEC-13). 키가 없으면(개발) 꺼져 있다.
3. 성공하면 실패 횟수가 0이 된다. 같은 IP는 10분에 로그인 30번까지 (D-80: 여러 계정을 돌려 가며 시도하는 매크로 방지).

### User Story 2 - 비밀번호 찾기·재설정 (Priority: P1)

1. **Given** 로그인 화면의 "비밀번호 찾기", **When** 이메일을 넣으면, **Then** 가입 여부와 관계없이 "입력한 이메일로 안내를 보냈습니다"를 보여주고, 가입된 이메일이면 6자리 인증번호를 보낸다 (USR-06).
2. 인증번호는 30분 뒤 만료, 한 번만 쓰고, 5번 틀리면 무효 (SEC-05). 같은 화면에서 새 비밀번호를 두 번 넣으면 바뀐다.
3. 바뀌면 모든 기기의 로그인이 끝나고 로그인 잠금도 풀린다.

### User Story 3 - 이메일 찾기 (Priority: P1)

1. **Given** 이름(앞뒤 공백 제외)과 전화번호(숫자만), **When** 찾으면, **Then** 맞는 계정의 이메일을 가려서 가입일과 함께 보여준다(여러 개면 모두, 탈퇴 제외). 없으면 "일치하는 회원 정보가 없습니다" (USR-08).
2. 계정 옆 [비밀번호 재설정]은 회원 번호가 아니라 10분짜리 임시 토큰으로 요청하고, 그 이메일로 인증번호를 보낸 뒤 USR-06과 같이 바꾼다 (D-26).
3. 같은 IP는 10분에 5번까지 찾을 수 있다.

### User Story 4 - 보안 헤더 (Priority: P1)

1. 모든 응답에 X-Frame-Options, X-Content-Type-Options: nosniff, Content-Security-Policy, Referrer-Policy가 붙고 HTTPS 요청에는 HSTS가 붙는다 (SEC-12).
2. 화면 스크립트는 모두 파일(/js)로 두고 인라인 스크립트를 쓰지 않는다. 밖에서 불러오는 것은 에디터(Toast UI)와 Turnstile뿐이다.

### Edge Cases

- 요청 제한은 서버 메모리로 센다. 이중화(SCL) 때는 공유 저장소로 옮긴다.
- 좋아요·팔로우·구독은 한 회원이 1분에 60번, 신고는 1시간에 20번, 가입 인증번호는 같은 IP가 10분에 10번까지 (6.6, D-80).
- 로드밸런서 뒤에서는 `FORWARD_HEADERS_STRATEGY=framework`로 실제 IP를 읽는다 (4.3). 직접 받는 서버에서 켜면 IP를 속일 수 있어 기본은 끔.
- 이메일 찾기 임시 토큰은 해시로만 저장하고, 쓰거나 만료되면 04:00 배치가 지운다 (4.5).

## Requirements *(mandatory)*

- **FR-001**: 실패 횟수는 오류 응답을 주더라도 저장한다 (`noRollbackFor`).
- **FR-002**: 없는 이메일·탈퇴 계정도 같은 응답(문구·시간)을 준다.
- **FR-003**: CSP: `default-src 'self'`, 스크립트는 self + uicdn.toast.com + challenges.cloudflare.com, `frame-ancestors 'none'`, `object-src 'none'`.

### Key Entities

- **이메일 찾기 임시 토큰(account_lookup_tokens)**: ERD 그대로. `users.failed_login_count`, `users.locked_until`을 엔티티에 매핑한다.

## Success Criteria

- **SC-001**: 비밀번호 대입은 계정마다 5번에 한 번 5분씩 막힌다.
- **SC-002**: 이메일 찾기 응답에 원래 이메일이 들어가지 않는다.
