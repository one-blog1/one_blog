# Data Model: 요청 제한 기록 공유

Crowfoot ERD "One Blog 메인블로그"(문서 655)에 먼저 더하고, Flyway `V24__create_rate_limit_hits.sql`로 반영한다 (constitution IV, FR-006).

## 새 요구사항 (Crowfoot, 그룹 "관리")

- 제목: `SEC-03 요청 제한 기록 공유 (D-80, SCL-01)`
- 내용:
  - IP·회원별 요청 제한(로그인, 가입 인증번호, 비밀번호 재설정, 이메일 찾기, 비밀번호 다시 확인, 신고, 좋아요·팔로우·구독)의 기록을 모든 앱이 같이 본다
  - 대상은 원문 대신 비밀 값을 섞은 해시로 둔다
  - 1시간이 지난 기록은 지운다
- 수용 기준: "1시간보다 오래된 기록이 남지 않는다" — `SELECT COUNT(*) FROM rate_limit_hits WHERE created_at < NOW(6) - INTERVAL 1 DAY` 기대 0 (배치가 하루 한 번이라 여유를 둠)

## 테이블 `rate_limit_hits` (요청 제한 기록)

설명: IP·회원별 요청 제한의 최근 요청 시각. 앱 여러 개가 같이 센다 (SEC-03, D-80, SCL-01). 1시간 지나면 지운다.

| 컬럼 | 타입 | NULL | 기본값 | 설명 |
|---|---|---|---|---|
| `id` | BIGINT | N | 자동 증가 (PK) | ID |
| `action` | VARCHAR(30) | N | | 제한 이름: login, signup-code, password-reset, find-email, reauth, report, toggle |
| `key_hash` | CHAR(64) | N | | 대상(IP 또는 회원 ID)의 HMAC-SHA256 (16진수) |
| `created_at` | DATETIME(6) | N | CURRENT_TIMESTAMP(6) | 요청 시각 (DB 시각) |

인덱스:
- `idx_rate_limit_hits_action_key_hash_created_at` (`action`, `key_hash`, `created_at`): 세기·대상별 정리
- `idx_rate_limit_hits_created_at` (`created_at`): 배치 정리

관계 없음(회원이 탈퇴해도 1시간 안에 지워지는 기록이라 외래 키를 두지 않는다). `updated_at`·`deleted_at` 없음(고치지 않는 기록, `post_views`와 같은 꼴, REQ-001의 예외 — 기록 테이블).
