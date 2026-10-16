# Tasks: 요청 제한 기록 공유 (032)

**Input**: `specs/032-shared-rate-limit/` (spec, plan, research, data-model, quickstart)

## Phase 1: ERD·마이그레이션

- [X] T001 Crowfoot 문서 655에 요구사항 "SEC-03 요청 제한 기록 공유 (D-80, SCL-01)"(그룹 관리, 수용 기준 SQL 포함)을 등록한다.
- [X] T002 Crowfoot 문서 655에 테이블 `rate_limit_hits`(data-model 그대로)를 더하고 T001 요구사항에 연결한다.
- [X] T003 `src/main/resources/db/migration/V24__create_rate_limit_hits.sql`: ERD와 같은 DDL(설명·인덱스 포함).

## Phase 2: US1·US2 공유 기록 (P1·P2)

- [X] T004 [US1] `common/security/RateLimiter.java`를 DB 방식으로 바꾼다 (plan "설계"). 생성자: `enabled`, `NamedParameterJdbcTemplate`, `PlatformTransactionManager`(REQUIRES_NEW 템플릿), pepper(`app.privacy.hash-pepper:${app.verification.code-pepper}`). 주석 갱신(SCL-01 해결).
- [X] T005 [US1] `common/security/RateLimiterTest.java`: "꺼져 있으면 검사하지 않는다"만 남긴다(DB 없이 만들 수 있게 jdbc·tx는 null 허용).
- [X] T006 [US1] `common/security/RateLimiterIntegrationTest.java`: quickstart 1~4.

## Phase 3: US3 정리 (P3)

- [X] T007 [US3] `batch/CleanupTask.java`: `DELETE FROM rate_limit_hits WHERE created_at < :hourAgo`. 클래스 주석에 한 줄.
- [X] T008 [US3] `RateLimiterIntegrationTest`에 정리 확인(quickstart 5).

## Phase 4: 문서·배포

- [X] T009 `DEPLOY.md`: "로컬과 같은 DB를 쓰는 동안 … CODE_PEPPER 같은 값" 안내를 개발 DB 분리(2026-10-16) 기준으로 고친다.
- [ ] T010 push → CI `unit`, 직접 실행 `full`.
- [ ] T011 PR → develop merge → `full`·품질 게이트 확인.
- [ ] T012 develop → main merge(버전 그대로) → Deploy 직접 실행 → 성공 확인.
- [ ] T013 문서 저장소: D-143, `security-status.md`(SEC-03 해결), 스펙 옮기기.
