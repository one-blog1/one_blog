# Implementation Plan: 통합 검색·메인 피드·블로그 검색

**Branch**: `main` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

## Summary

Flyway V10으로 `recent_searches`를 추가한다. 검색과 피드는 `search` 패키지에서 바인딩 파라미터를 쓰는 SQL(NamedParameterJdbcTemplate)로 글·블로그 ID 한 페이지를 고르고, 한 줄 모양은 008의 `PostCardService`, 002의 `BlogQueryService.toListItems`가 만든다. 차단(013)처럼 결과에서 뺄 조건은 `SearchFilter` 빈으로 더한다.

## Research

### R1. FULLTEXT 검색식

- **Decision**: 글자·숫자·_만 남긴 2글자 이상 낱말마다 `+"낱말"`(BOOLEAN MODE). 정렬의 관련도는 MATCH 점수 + 태그·닉네임 일치 가산(10).
- **Rationale**: NATURAL 모드는 ngram 조각 하나만 맞아도 걸려 결과가 지저분하다. 따옴표로 낱말 안의 ngram이 붙어 있어야 맞게 한다.

### R2. SQL 조립

- **Decision**: 조건은 코드에 적힌 고정 문자열 조각을 OR로 잇고, 값(:ft, :tagId, :authorId, :viewerId)은 모두 바인딩한다.

### R3. 최근 검색어 저장 시점

- **Decision**: 검색 API(GET) 안에서 로그인한 회원이 1페이지를 볼 때 `INSERT ... ON DUPLICATE KEY UPDATE`, 10개 넘으면 오래된 것부터 지운다.
- **Trade-off**: GET에 부수 효과가 있다. 남는 것이 본인 검색어뿐이라 1차는 받아들인다.

## Constitution Check

| 원칙 | 확인 | 결과 |
|---|---|---|
| III. SQL 바인딩 | 사용자 값은 바인딩, 검색식 기호 제거 | ✅ |
| III. 비공개 정보 | 검색은 전체 공개 블로그만, 피드 개인 탭은 볼 수 있는 블로그만 | ✅ |
| IV. ERD 우선 | V10은 ERD DDL 그대로 | ✅ |
