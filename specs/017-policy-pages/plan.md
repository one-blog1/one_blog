# Implementation Plan: 개인정보 처리방침·이용약관 화면

**Branch**: `main` | **Date**: 2026-10-12 | **Spec**: [spec.md](spec.md)

## Summary

DB 변경 없음(ERD 그대로, 동의 시각 컬럼은 V1에 이미 있음). 정적 화면 `privacy.html`, `terms.html`을 더하고 `SecurityConfig` 비회원 허용 목록에 넣는다. 하단 링크는 모든 화면이 불러오는 `header.js`가 `<footer class="site-footer">`로 한 번에 붙인다. 가입 ③단계는 `signup.html`·`signup.js`에서 전체 동의와 버튼 잠금을 더한다. 서버 검사(`SignupRequest`의 `@AssertTrue`)는 그대로 둔다.

## Research

### R1. 하단 링크를 어디에 넣나

- **Decision**: `header.js`의 `renderFooter()`. 모든 HTML이 이미 이 스크립트를 불러온다.
- **Alternatives**: HTML 19개에 직접 넣기(빠뜨리기 쉽고 바꿀 때 19곳을 고쳐야 함).

### R2. 문안의 기준

- **Decision**: 개인정보보호위원회 「개인정보 처리방침 작성지침」(2026-04 개정)의 항목 순서와 분석서 4.4~4.6의 값. 메일 발송(Google)과 사람 확인(Cloudflare)은 국외 이전으로 적는다.
- **Trade-off**: 공개 전에 법률 검토가 필요하다(4.6).

## Constitution Check

| 원칙 | 확인 | 결과 |
|---|---|---|
| I. 요구사항 기준 | 4.6 동의 방법·안내를 그대로 구현, 보류 항목 없음 | ✅ |
| III. 보안 기준선 | 동의는 서버에서도 검사, 새 화면은 정적이라 사용자 입력 없음 | ✅ |
| IV. ERD 우선 | 테이블 변경 없음 | ✅ |
| V. 단순함 | 서버 템플릿 없이 정적 HTML | ✅ |
