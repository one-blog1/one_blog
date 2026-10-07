# Implementation Plan: 블로그 운영, 회원탈퇴, 04:00 배치

**Branch**: `main` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

## Summary

Flyway V12로 `blog_transfer_requests`, `shedlock`을 추가한다. 운영 기능은 `blog.ops` 패키지(`BlogManageService`, `BlogTransferService`, `BlogCloseService`, `BlogAudience`), 회원탈퇴는 `account.WithdrawalService`, 배치는 `batch` 패키지(`DailyBatch`가 `DailyTask` 빈들을 순서대로)로 나눈다. 013(신고·제재 기록 1년 보관, 블랙리스트 삭제)은 `DailyTask`를 하나 더 두면 된다.

## Research

### R1. 폐쇄 시각

- **Decision**: `BlogClock.closeAt(now)` = 한국 날짜 + 7일 04:00을 서버 시간대 LocalDateTime으로. 3일·1일 전은 한국 날짜 차이로 고른다.

### R2. 알림 중복 방지

- **Decision**: 즉시·철회 알림 키에는 누른 시각, 3일·1일 전 알림 키에는 예정 시각을 넣는다. 배치를 다시 돌려도 같은 알림은 한 번.

### R3. 회원탈퇴와 글 표시

- **Decision**: 회원탈퇴는 작성자 연결을 끊지 않는다(`author_detached` 그대로) → "탈퇴한 회원"(USR-05). 블로그 탈퇴·강퇴는 끊는다 → "탈퇴한 계정"(D-33).

### R4. 배치 잠금

- **Decision**: ShedLock 라이브러리 대신 같은 `shedlock` 테이블을 쓰는 30줄짜리 `SchedulerLock`(lock_until이 지난 행만 가져감).
- **Rationale**: 작업이 하나뿐이라 라이브러리의 다른 기능이 필요 없고, 테이블 형식이 같아 나중에 ShedLock으로 바꿔도 DB는 그대로다.

### R5. 글 완전 삭제 순서

- **Decision**: 파일 → 답글 → 첫 댓글 → 글. `fk_comments_comments`에는 CASCADE가 없어 MySQL이 같은 테이블 안에서 지우는 순서를 보장하지 않는다.

## Constitution Check

| 원칙 | 확인 | 결과 |
|---|---|---|
| III. 권한은 서버에서 | 운영 API마다 멤버십 확인 | ✅ |
| III. 개인정보 | 멤버 목록 이메일·전화번호 가림, 탈퇴 30일 뒤 삭제 | ✅ |
| IV. ERD 우선 | V12는 ERD DDL 그대로 | ✅ |
