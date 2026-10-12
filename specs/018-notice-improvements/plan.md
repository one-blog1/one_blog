# Implementation Plan: 메인 공지 개선

**Branch**: `main` | **Date**: 2026-10-12 | **Spec**: [spec.md](spec.md)

## Summary

ERD v11에 `posts.notice_category`와 `ck_posts_notice_category`를 더했고(REQ-025, D-122), V18로 반영한다. 서버는 `NoticeCategory` enum, `NoticeService.update`, `PUT /api/admin/notices/{id}`, 목록의 종류 거르기를 더한다. 화면은 `notice.html`을 목록·상세 겸용으로 바꾸고, 메인 `notice-strip.js`를 2초마다 넘어가는 한 줄로, 관리자 공지 탭에 종류·수정 버튼·제목 링크를 더한다.

## Research

### R1. 종류를 어디에 저장하나

- **Decision**: `posts.notice_category` 컬럼(MAIN_NOTICE만 값). 공지는 이미 posts에 있고(BRD-10, 게시판 공통 모듈) 종류는 공지에만 있는 속성이다.
- **Alternatives**: 카테고리 테이블(`categories`)은 블로그에 속해(blog_id NOT NULL) 메인 공지에 맞지 않는다. 제목 앞 머리말([업데이트])은 거르기·검사가 안 된다.

### R2. 공지 목록 화면

- **Decision**: `notice.html` 하나에서 `?id`가 있으면 상세, 없으면 목록. 비회원 허용 목록과 기존 링크(`/notice.html?id=`)를 그대로 쓴다.

### R3. 2초 공지 줄

- **Decision**: 공지 하나만 보이는 줄에서 `setInterval` 2초로 다음 공지. 마우스·키보드 초점이 있으면 멈춘다(WCAG 2.2.2 움직이는 내용 멈춤). `prefers-reduced-motion`이면 효과 없이 바꾼다. 화면 읽기 프로그램에는 계속 읽어 주지 않게 `aria-live="off"`.
- **Alternatives**: CSS 애니메이션만으로 돌리기는 개수가 바뀔 때 맞추기 어렵고 멈춤 처리가 번거롭다.

### R4. 수정 알림

- **Decision**: 수정은 알림을 다시 보내지 않는다. 오타 하나 고칠 때마다 전체 회원에게 가면 알림이 소음이 된다. 내용이 크게 바뀌면 새 공지를 올린다.

## Constitution Check

| 원칙 | 확인 | 결과 |
|---|---|---|
| I. 요구사항 기준 | BRD-10 확장, D-122로 결정 기록 | ✅ |
| III. 보안 기준선 | 수정은 `/api/admin/**`(ROLE_ADMIN)이고 서비스가 다시 확인하지 않아도 SecurityConfig가 막음. 본문은 서버가 걸러 `innerHTML`, 나머지는 `textContent` | ✅ |
| IV. ERD 우선 | Crowfoot ERD v11을 먼저 고치고 V18 | ✅ |
| V. 단순함 | 새 테이블·라이브러리 없음 | ✅ |
