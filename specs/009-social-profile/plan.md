# Implementation Plan: 팔로우·블로그 구독·프로필·회원정보 수정

**Branch**: `main` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

## Summary

Flyway V9로 ERD의 `follows`, `blog_subscriptions`를 추가한다. 팔로우는 `social`, 구독은 `blog.subscription`, 프로필은 `profile`, 회원정보 수정은 `account` 패키지에 둔다. 일부 공개 블로그의 "구독하면 링크 없이"(D-50)는 `BlogAccessService` 한 곳에 조건을 더한다. 비밀번호 변경은 `RefreshTokenRepository.revokeOthers`로 지금 세션을 뺀 로그인을 한 번에 폐기한다.

## Research

### R1. 프로필 주소

- **Decision**: `/users/{닉네임}`. 닉네임은 중복 불가이고 6.5에 "프로필 주소에 쓰임"으로 적혀 있다.
- **Trade-off**: 닉네임을 바꾸면 예전 주소는 끊긴다. 1차는 받아들인다.

### R2. 구독 취소와 접근 검사

- **Decision**: 구독할 때만 접근 검사를 한다. 취소는 블로그가 있으면 언제나 된다.
- **Rationale**: 비공개로 바뀐 블로그(D-37)의 구독자가 구독을 끊을 수 없으면 안 된다.

### R3. 토글의 동시성

- **Decision**: 회원 행 잠금(`findForUpdateById`) 뒤에 있는지 보고 지우거나 넣는다. 그래도 겹치면 UNIQUE가 막고 409.

## Constitution Check

| 원칙 | 확인 | 결과 |
|---|---|---|
| III. 본인만 수정 | `/api/me/**`는 로그인한 본인 행만 고친다 | ✅ |
| III. 비밀번호 | 현재 비밀번호 확인, bcrypt, 요청 로그에 남지 않게 toString 가림 | ✅ |
| III. XSS | 닉네임·소개는 textContent로만 | ✅ |
| IV. ERD 우선 | V9는 ERD DDL 그대로 | ✅ |
