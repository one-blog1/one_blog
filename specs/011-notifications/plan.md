# Implementation Plan: 알림

**Branch**: `main` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

## Summary

Flyway V11로 `notifications`, `notification_settings`를 추가한다. 각 기능이 이미 둔 Listener(CommentListener, LikeListener, FollowListener, SubscriptionListener, NoticeListener, AdminListener, PostExtension.afterDelete)와 이번에 더한 `JoinListener`를 `NotificationHooks` 하나가 구현해 `NotificationService`로 보낸다. 보내기는 `INSERT IGNORE ... SELECT`로 설정·회원 상태 확인과 중복 방지를 한 문장에 한다. 화면은 `header.js`가 일반 회원에게만 `notifications.js`를 불러 종 아이콘과 사이드바를 붙인다.

## Research

### R1. 알림 실패가 원래 기능을 망치지 않게

- **Decision**: `NotificationService`에는 `@Transactional`을 붙이지 않고 예외를 잡아 기록만 한다.
- **Rationale**: 같은 트랜잭션에서 `@Transactional` 메서드가 예외를 던지면 바깥 트랜잭션이 롤백 전용이 된다. MySQL은 실패한 문장 하나로 트랜잭션 전체가 깨지지 않는다.

### R2. 보관 기간

- **Decision**: 목록·안 읽은 수에서 `created_at >= 지금 - 보관 일수`만 보이고, 실제 삭제는 04:00 배치(012).

## Constitution Check

| 원칙 | 확인 | 결과 |
|---|---|---|
| III. 본인 데이터 | 모든 쿼리에 `user_id = 나` | ✅ |
| III. XSS | 메시지 textContent, 링크는 사이트 안 주소만 | ✅ |
| IV. ERD 우선 | V11은 ERD DDL 그대로 | ✅ |
