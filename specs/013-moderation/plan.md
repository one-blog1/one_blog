# Implementation Plan: 제재·블랙리스트·신고·차단

**Branch**: `main` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

## Summary

Flyway V13으로 ERD의 `blog_blacklists`, `blacklist_inquiries`, `sanctions`, `reports`, `blocks`를 추가한다. 패키지: `block`(차단), `sanction`(제재·블랙리스트·관리자 블로그장 제재), `report`(신고). 다른 기능에 끼어드는 자리는 인터페이스로 둔다: `JoinGate`(참여 신청 전: 블랙리스트 막기, 차단 자동 거절), `FollowGate`(차단한 사이 팔로우 막기), `HiddenAuthors`(블로그 글·댓글 목록에서 가릴 작성자), `SearchFilter`(010, 검색·피드·태그 목록). 정지는 `BlogAccessService`가 판단한다.

## Research

### R1. 블랙리스트 해시

- **Decision**: `HMAC-SHA256(pepper, 종류 + 정리한 값)`. pepper는 `PRIVACY_HASH_PEPPER`(없으면 `CODE_PEPPER`).
- **Rationale**: 전화번호는 경우의 수가 적어 그냥 SHA-256이면 되돌릴 수 있다 (4.6).

### R2. 신고 저장

- **Decision**: JDBC로 넣고 `target_snapshot`은 서버가 만든 JSON 문자열(작성자 ID·블로그 ID 포함). 처리할 때 `JSON_EXTRACT`로 읽는다.
- **Rationale**: 대상 종류가 여럿이라 엔티티 대신 SQL이 단순하고, 대상이 지워져도 처리에 필요한 값이 남는다.

### R3. 처리 화면 권한

- **Decision**: 블로그 신고 목록은 멤버 관리 권한이 있는 사람만, 그중 신고 대상 본인에 대한 신고는 목록에서 빼고 처리도 막는다 (D-95).

### R4. 블로그장 승계

- **Decision**: `sub_owner_since`가 가장 이른 활성 부블로그장. 없으면 `BlogCloseService.schedule(blog, "OWNER_REVOKED")`.

## Constitution Check

| 원칙 | 확인 | 결과 |
|---|---|---|
| III. 권한은 서버에서 | 제재·처리·문의 처리마다 멤버십 확인, 관리자 API는 ADMIN | ✅ |
| III. 개인정보 | 블랙리스트는 HMAC 해시만, 원문 저장 없음 | ✅ |
| III. SQL 바인딩 | 신고·차단 SQL은 고정 문자열 + 바인딩 | ✅ |
| IV. ERD 우선 | V13은 ERD DDL 그대로 | ✅ |
