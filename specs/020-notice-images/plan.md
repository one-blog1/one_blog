# Implementation Plan: 메인 공지에 사진 넣기

**Branch**: `main` | **Date**: 2026-10-12 | **Spec**: [spec.md](spec.md)

## Summary

DB 변경 없음(공지도 `posts` 행이라 `files.post_id`로 연결). 서버: `PostImageExtension`의 연결 규칙을 `attachImages(post, uploaders)`·`detachAll(post)`·`validateCount(markdown)`로 꺼내 블로그 글과 `NoticeService`(만들기·고치기·지우기)가 같이 쓴다. 화면: `admin.html`에 Toast UI를 불러오고 `components/editor.js`(높이 옵션 추가)를 공지 칸에 쓴다.

## Research

### R1. 새 테이블이나 목적(purpose)을 만들까

- **Decision**: 아니다. 공지는 `post_type = MAIN_NOTICE`인 글이라 글 이미지(`purpose = POST`)와 같은 연결·정리 규칙을 그대로 쓴다.
- **Alternatives**: `NOTICE` 목적 추가 — ERD·마이그레이션이 필요하고 규칙이 같아 얻는 게 없다.

### R2. 고친 관리자의 사진

- **Decision**: 공지는 관리자 누구나 고칠 수 있어, 처음 쓴 관리자와 지금 고치는 관리자가 올린 사진을 연결한다(블로그 글은 작성자만).

## Constitution Check

| 원칙 | 확인 | 결과 |
|---|---|---|
| III. 보안 기준선 | 본문은 jsoup으로 거름, 이 사이트 사진만, 파일 검사(SEC-08) 그대로 | ✅ |
| IV. ERD 우선 | 테이블 변경 없음 | ✅ |
| V. 단순함 | 글쓰기 에디터와 이미지 연결 규칙 재사용 | ✅ |
