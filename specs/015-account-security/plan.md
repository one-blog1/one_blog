# Implementation Plan: 계정 보안 세부

**Branch**: `main` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

## Summary

Flyway V15로 `account_lookup_tokens`를 추가한다. `AuthService`에 계정 잠금·사람 확인을 넣고(`User.recordLoginFailure`), `RateLimiter`(메모리 슬라이딩 윈도)를 로그인·가입 인증번호·비밀번호 찾기·이메일 찾기·좋아요·팔로우·구독·신고에 건다. 비밀번호 재설정은 001의 `verification_codes`(PASSWORD_RESET)와 메일을 다시 쓴다. Turnstile은 `TurnstileVerifier`(키가 없으면 꺼짐). 보안 헤더는 Spring Security `headers()`에 CSP·Referrer-Policy를 더하고, 인라인 스크립트(공지·관리자 로그인)를 파일로 옮긴다.

## Research

### R1. 요청 제한 저장소

- **Decision**: 1차는 서버 메모리(`ConcurrentHashMap` + 시각 큐). 테스트는 `app.rate-limit.enabled=false`(같은 IP로 수백 번 로그인), 규칙은 단위 테스트로 본다.
- **Trade-off**: 서버가 여러 대면 대수만큼 느슨해진다 → 이중화 때 DB·Redis로.

### R2. 사람 확인

- **Decision**: 3번 틀린 계정은 Turnstile 토큰이 있어야 비밀번호를 확인한다. 서버가 `CAPTCHA_REQUIRED`(또는 `LOGIN_FAILED_CAPTCHA`)를 주면 화면이 위젯을 띄운다.
- **Rationale**: 모든 화면에 띄우면 불편하다 (6.7).

### R3. 재설정 인증번호 다시 받기

- **Decision**: 1분 안에 다시 요청하면 오류 대신 조용히 건너뛴다(화면 문구는 같게). 가입 여부를 드러내지 않기 위해서다.

## Constitution Check

| 원칙 | 확인 | 결과 |
|---|---|---|
| III. 보안 기준선 | 잠금·사람 확인·요청 제한·보안 헤더·CSP | ✅ |
| III. 비밀값 | Turnstile 키는 환경변수만 | ✅ |
| III. 개인정보 | 이메일 가림, 토큰은 해시만 | ✅ |
| IV. ERD 우선 | V15는 ERD DDL 그대로 | ✅ |
