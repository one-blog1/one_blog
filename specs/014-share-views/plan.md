# Implementation Plan: 공유·조회수

**Branch**: `main` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

## Summary

Flyway V14로 `post_views`를 추가한다. `ViewCounter`가 글 상세 API(`GET /api/posts/{id}`)에서 접근 검사를 통과한 뒤 따로 센다(상세 조회는 읽기 전용 트랜잭션이라 그 안에서 쓰지 않는다). 글 화면 주소 `/blog/{주소}/posts/{번호}`는 정적 `post.html`을 읽어 `</head>` 앞에 og 태그를 넣어 돌려준다(`PostPageRenderer`).

## Research

### R1. 미리보기를 채울지 판단

- **Decision**: `BlogAccessService.check(blog, key, null)` — 비회원 기준으로 볼 수 있을 때만 채운다. 렌더러에는 트랜잭션을 걸지 않는다(검사 실패 예외를 잡아도 롤백 전용이 되지 않게).

### R2. 봇 거르기

- **Decision**: User-Agent가 비었거나 bot·crawl·spider·facebookexternalhit·kakaotalk-scrap 등을 포함하면 세지 않는다. 완벽하지 않지만 하루 1번 규칙과 함께 부풀리기를 충분히 줄인다.

## Constitution Check

| 원칙 | 확인 | 결과 |
|---|---|---|
| III. XSS | og 값 이스케이프, 본문은 HTML에 넣지 않음 | ✅ |
| III. 비공개 정보 | 비회원이 못 보는 글은 기본 태그만 | ✅ |
| IV. ERD 우선 | V14는 ERD DDL 그대로 | ✅ |
