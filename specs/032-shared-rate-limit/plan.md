# Implementation Plan: 요청 제한 기록 공유

**Branch**: `feature/shared-rate-limit` | **Date**: 2026-10-16 | **Spec**: [spec.md](./spec.md)

**Input**: spec 확인(2026-10-16). 사용자가 "그냥 진행해 … 메인에 배포까지"로 plan·tasks 확인을 건너뛰게 함(2026-10-16). 사용자에게 보이지 않는 변경이라 버전은 올리지 않고 다시 배포(0.20 때와 같은 방식).

## Summary

`RateLimiter`의 기록을 앱 메모리에서 DB 테이블 `rate_limit_hits`로 옮긴다. 부르는 쪽(7가지 제한)은 바꾸지 않는다.

| 파일 | 바꾸는 내용 |
|---|---|
| Crowfoot ERD 655 | 요구사항 "SEC-03 요청 제한 기록 공유" + 테이블 `rate_limit_hits`(그룹 관리) |
| `db/migration/V24__create_rate_limit_hits.sql` | 테이블·인덱스 (ERD DDL 그대로) |
| `common/security/RateLimiter.java` | 메모리 대신 DB: 새 트랜잭션에서 창 밖 기록 지우기 → 세기 → 넘으면 429 → 아니면 기록. 대상은 HMAC 해시. DB 오류면 경고 로그 후 통과 |
| `batch/CleanupTask.java` | 1시간 지난 `rate_limit_hits` 지우기 |
| `common/security/RateLimiterTest.java` | 메모리 판단 테스트를 빼고 "꺼져 있으면 검사 안 함"만 |
| `common/security/RateLimiterIntegrationTest.java` (새) | 앱 2개 공유, 창, 해시, 정리 (quickstart) |
| `DEPLOY.md`, 문서 저장소 `security-status.md` | DB 분리 완료·SEC-03 해결 |

## Technical Context

- Java 21, Spring Boot 4.1.1, `NamedParameterJdbcTemplate`, `TransactionTemplate`(`PROPAGATION_REQUIRES_NEW`)
- MySQL 8 (운영 Crowfoot MySQL #1, 개발 #2, CI 서비스)
- 테스트: 통합은 `...IntegrationTest`(MySQL), 단위는 `...Test`
- 성능: 요청당 쿼리 3개(지우기·세기·쓰기), 인덱스 `(action, key_hash, created_at)`로 대상 범위만 읽음

## Constitution Check

| 원칙 | 확인 |
|---|---|
| I | SEC-03·USR-08·6.6 횟수 그대로. 새 결정 D-143(요청 제한 기록을 DB에 공유, 대상 해시) |
| III | SQL은 바인딩만. IP 원문을 저장하지 않음(HMAC, REQ-002). 비밀 값은 환경변수 |
| IV | ERD 먼저 → V24. 서버 메모리 상태 제거(SCL-01) |
| V | 새 저장소 없음(Redis X). 부르는 쪽 그대로 |

통과.

## 설계 (research 참고)

`check(action, key, max, window)`:
1. 꺼져 있으면 끝.
2. `hash = HMAC(pepper, key)`.
3. 새 트랜잭션에서:
   - `DELETE FROM rate_limit_hits WHERE action=:a AND key_hash=:h AND created_at <= NOW(6) - INTERVAL :ms*1000 MICROSECOND`
   - `SELECT COUNT(*) ... created_at > (같은 기준)`
   - count ≥ max → 거절 표시, 아니면 `INSERT (action, key_hash)`
4. 거절이면 지금과 같은 429(`retryAfterSeconds = max(1, window초 / max)`).
5. `DataAccessException`이면 경고 로그 후 통과.

## Project Structure

```text
specs/032-shared-rate-limit/ spec.md plan.md research.md data-model.md quickstart.md tasks.md
src/main/resources/db/migration/V24__create_rate_limit_hits.sql
src/main/java/com/oneblog/common/security/RateLimiter.java
src/main/java/com/oneblog/batch/CleanupTask.java
src/test/java/com/oneblog/common/security/RateLimiterTest.java
src/test/java/com/oneblog/common/security/RateLimiterIntegrationTest.java
```

## Complexity Tracking

없음.
