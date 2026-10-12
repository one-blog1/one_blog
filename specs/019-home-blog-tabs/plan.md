# Implementation Plan: 내 블로그 새 글 블로그별 보기, 읽은 글 표시

**Branch**: `main` | **Date**: 2026-10-12 | **Spec**: [spec.md](spec.md)

## Summary

DB·API 변경 없음. 화면만 바꾼다: `index.html`의 "내 블로그의 새 글"에 `.tabs` 단추 줄을 더하고, `js/pages/home/home.js`가 [전체]면 기존 `GET /api/feed?tab=myblogs`, 블로그를 고르면 기존 `GET /api/blogs/{slug}/posts`를 부른다. 읽은 글은 `js/lib/ui.js`의 `markRead`·`isRead`(localStorage)를 글 화면(`post.js`)과 글 목록 부품(`post-item.js`)이 쓰고, `app.css`의 `.is-read`·`.read-mark`로 표시한다. 좋아요는 `icons.js`에 하트를 더해 `comments.js`의 `renderLike`가 하트+숫자만 그린다.

## Research

### R1. 읽은 글을 어디에 기억하나

- **Decision**: 이 기기의 localStorage에 읽은 글 번호. 처음엔 브라우저 방문 기록(`:visited`)으로 했으나 보안상 색만 바꿀 수 있어 구분이 약했다(사용자 의견). localStorage면 굵기·사진 흐림·"읽음" 표시까지 할 수 있다.
- **Alternatives**: 서버의 조회 기록(`post_views`)으로 "읽음"을 내려 주기 — 기기가 바뀌어도 유지되지만, 목록 API마다 회원별 조회 여부를 붙여야 하고 비회원은 안 된다. 필요해지면 그때 한다.
- **Trade-off**: 보안상 색만 바꿀 수 있고, 방문 기록을 지우면 사라진다.

## Constitution Check

| 원칙 | 확인 | 결과 |
|---|---|---|
| III. 사용자 입력 | 블로그 이름은 textContent로만 (ui.el) | ✅ |
| IV. 서버에 상태 없음 | 고른 블로그·읽은 글 모두 브라우저에만 | ✅ |
| V. 단순함 | 새 API 없이 있는 API 두 개 재사용 | ✅ |
