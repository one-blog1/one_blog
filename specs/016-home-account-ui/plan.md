# Implementation Plan: 메인 홈·상단 메뉴·내 정보 정리

**Branch**: `main` | **Date**: 2026-10-08 | **Spec**: [spec.md](spec.md)

## Summary

DB 변경 없음(ERD 그대로). 서버는 비밀번호 재확인만 더한다: `AccessTokenService`에 `REAUTH` 용도 JWT(sub=회원, sid=로그인 행, 10분), `ReauthService`가 비밀번호를 확인하고 `REAUTH_TICKET` 쿠키(HttpOnly, SameSite=Strict, Path=/api/me)를 준다. `AccountController`의 프로필·비밀번호 수정은 쿠키를 검사한다. `/api/me` 응답에 프로필 사진 주소를 더한다. 나머지는 화면(header.js, index.html + home.js, account, settings, withdraw, app.css).

## Research

### R1. 재확인 상태를 어디에 두나

- **Decision**: 서명한 JWT 쿠키. 이미 쓰는 HS256 키로 서명하고 `typ=REAUTH`로 Access Token·가입 티켓과 구분한다.
- **Alternatives**: 서버 메모리(이중화 때 깨짐, 4.3), DB 컬럼(ERD 변경이 필요하고 값이 금방 버려짐).
- **Trade-off**: 10분 안에는 되돌릴 수 없지만 로그인 행(sid)과 묶어 로그아웃·다른 기기 로그아웃 뒤에는 쓸 수 없다(sid의 로그인 행이 살아 있어야 요청이 인증됨).

### R2. 비밀번호 변경

- **Decision**: 재확인이 있으면 현재 비밀번호는 묻지 않는다. 요청에 `currentPassword`가 있으면 무시한다.

## Constitution Check

| 원칙 | 확인 | 결과 |
|---|---|---|
| III. 보안 기준선 | 서버에서 재확인 검사, 시도 횟수 제한, HttpOnly·Strict 쿠키 | ✅ |
| III. 사용자 입력 | 닉네임·블로그 이름은 textContent로만 | ✅ |
| IV. ERD 우선 | 테이블 변경 없음 | ✅ |
