# Tasks: 계정 보안 세부

- [X] T001 V15 `account_lookup_tokens`, `User` 실패 횟수·잠금
- [X] T002 `RateLimiter`, `TurnstileVerifier`, 로그인·관리자 로그인에 잠금·사람 확인·IP 제한
- [X] T003 `PasswordResetService`, `EmailFindService`, `AccountRecoveryController`, 재설정 메일
- [X] T004 요청 제한: 가입 인증번호, 좋아요·팔로우·구독, 신고
- [X] T005 보안 헤더·CSP, 인라인 스크립트를 파일로(공지, 관리자 로그인), 프록시 헤더 설정
- [X] T006 [P] 화면 `find-account.html`, `find-account.js`, `captcha.js`, 로그인 잠금 안내
- [X] T007 04:00 배치에서 임시 토큰 정리
- [X] T008 테스트 `AccountSecurityIntegrationTest`, `RateLimiterTest`
- [ ] T009 Turnstile 키 등록 후 실제 화면에서 확인 (사용자)
- [X] T010 `./gradlew test` 통과 (182개, 2026-10-08)
