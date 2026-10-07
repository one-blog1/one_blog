# Implementation Plan: 카테고리·태그

**Branch**: `main` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

## Summary

Flyway V8로 ERD의 `categories`, `post_tags`와 004에서 미룬 `fk_posts_categories`를 추가한다. 카테고리는 `category` 패키지(엔티티·서비스·API)로, 글과의 연결은 004의 `PostExtension`을 구현한 `CategoryExtension`, `PostTagExtension`으로 붙여 `PostService`를 고치지 않는다. 태그 저장은 002의 `TagService`에 글용 메서드(INSERT IGNORE + 통째로 바꾸기)를 더한다. 여러 블로그의 글이 섞이는 목록은 `PostCardService`가 한 줄 모양(`PostCard`)을 만들고, 태그 목록(이번)과 검색·피드(010)가 함께 쓴다.

## Research

### R1. 카테고리 삭제

- **Decision**: 소프트 삭제(`deleted_at`)하고 같은 트랜잭션에서 그 카테고리 글의 `category_id`를 NULL로 바꾼다.
- **Rationale**: 글은 블로그장 것이 아니라 작성자 것이라 카테고리를 지운다고 글이 사라지면 안 된다. 외래 키도 SET NULL이라 블로그 완전 삭제(012) 때와 결과가 같다.

### R2. 태그 목록의 공개 기준

- **Decision**: `blogs.visibility = 'PUBLIC'`이고 폐쇄·숨김·삭제되지 않은 블로그의 보이는 글만. 로그인 여부와 관계없이 같다.
- **Rationale**: 태그 페이지는 검색과 같은 성격이라 6.1 기준을 따른다. 일부 공개 블로그는 링크를 받은 사람만 봐야 해서 뺀다.

### R3. 글 태그 저장

- **Decision**: 저장할 때마다 `post_tags`를 지우고 다시 넣는다. 태그 행은 INSERT IGNORE로 만든다(동시 요청에도 `uk_tags_name`이 하나만 남김). 입력 순서대로 넣어 `post_tags.id` 순서가 보여주는 순서가 된다.

## Constitution Check

| 원칙 | 확인 | 결과 |
|---|---|---|
| III. 권한은 서버에서 | 카테고리 관리는 매 요청 멤버십(`canManagePosts`)으로 | ✅ |
| III. SQL 바인딩 | 태그 목록·태그 저장 SQL은 바인딩 파라미터만 | ✅ |
| III. XSS | 화면은 카테고리·태그를 textContent로만 넣는다 | ✅ |
| IV. ERD 우선 | V8은 ERD DDL 그대로 | ✅ |
