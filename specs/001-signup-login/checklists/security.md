# Security Baseline Checklist: 001 회원가입·로그인·로그아웃

**Purpose**: constitution III(처음부터 지키는 보안 기준선)을 001 코드가 지키는지 확인한다 (tasks T049)
**Created**: 2026-10-07
**Feature**: [spec.md](../spec.md)

## constitution III

- [x] 비밀번호는 bcrypt로만 저장한다 — `PasswordConfig`(BCrypt 강도 10), `SignupService`가 `passwordEncoder.encode`만 저장 (SEC-01)
- [x] 역할·상태는 매 요청 DB로 확인한다 — `JwtCookieAuthenticationFilter` → `SessionService.validate`가 refresh_tokens 행과 users 상태를 읽음 (SEC-07, SEC-11)
- [x] SQL은 파라미터 바인딩만 쓴다 — Spring Data 메서드와 `@Query`(JPQL, `:param`)만 사용. `git grep`으로 문자열로 만든 SQL 없음 확인
- [x] 사용자 입력은 `textContent`로만 화면에 넣는다 — `src/main/resources/static/js`에 `innerHTML` 사용 없음 확인 (SEC-06)
- [x] 이메일·전화번호 가리기 — 이 기능은 본인 정보만 보여 해당 없음 (`/api/me`는 id, 닉네임, 역할만)
- [x] 비밀값은 저장소에 없다 — DB·JWT·HMAC·메일 비밀값은 `.env`/환경변수로만. `.env`는 `.gitignore`. `git grep`으로 원문 없음 확인. 테스트용 고정 키는 `application-test.yml`의 테스트 전용 값

## 이 기능의 추가 확인

- [x] 인증번호·Refresh Token은 해시로만 저장 (HMAC-SHA256, SHA-256) — `TokenHasher`
- [x] 가입 여부 비노출 — 인증번호 요청 응답이 가입 여부와 무관하게 같고, 메일은 비동기 발송. 로그인 실패 문구 동일, 없는 이메일도 더미 bcrypt 비교 (D-28)
- [x] 쿠키 인증 요청의 CSRF 검사 — Spring Resource Server 설정 대신 직접 필터를 둬서 쿠키 인증 요청도 CSRF 검사를 받음 (SEC-10, research R2)
- [x] 로그인 쿠키는 HttpOnly, SameSite, 운영에서 Secure (`COOKIE_SECURE`) (SEC-04)
- [x] 오류 응답에 내부 메시지를 노출하지 않음 — `GlobalExceptionHandler`

## Notes

- 로그인 잠금(SEC-03), CAPTCHA(SEC-13), IP 요청 제한(D-80), 보안 헤더 세부 값(SEC-12)은 로드맵 015 후속 작업이다.
