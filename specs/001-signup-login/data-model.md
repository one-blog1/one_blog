# Data Model: 001 회원가입·로그인·로그아웃

기준: Crowfoot ERD "One Blog 메인블로그" (https://crowfoot.java21.net/workspaces/58/models/655). 테이블·컬럼 이름, 타입, 제약은 ERD 그대로이며 이 문서에서 새로 설계하지 않는다 (constitution IV).

이 기능에서 만드는 테이블은 `users`, `verification_codes`, `refresh_tokens` 3개다. 마이그레이션 파일은 `V1__create_member_auth_tables.sql`이고, ERD에서 내보낸 DDL 중 이 3개 테이블과 그 사이의 외래 키·인덱스만 담는다. 모든 테이블은 `DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci`로 만든다.

## users (회원)

| 컬럼 | 타입 | 이 기능에서의 쓰임 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 회원 ID, Access Token의 `sub` |
| email | VARCHAR(255) NULL, UNIQUE | 로그인 아이디. 소문자·앞뒤 공백 제거 후 저장 |
| login_id | VARCHAR(30) NULL, UNIQUE | 관리자 전용. 이 기능에서는 항상 NULL |
| password_hash | VARCHAR(100) NOT NULL | bcrypt 해시 |
| name | VARCHAR(50) NULL | 앞뒤 공백 제거, 1~50자 |
| nickname | VARCHAR(12) NULL, UNIQUE | 2~12자 한글·영문·숫자, 예약어 불가. 콜레이션상 대소문자 무시로 유일 |
| phone | VARCHAR(20) NULL | 숫자만 10~11자리, 중복 허용 |
| role | VARCHAR(20) NOT NULL DEFAULT 'USER' | 가입하면 USER |
| status | VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' | ACTIVE만 로그인 가능 |
| terms_agreed_at | DATETIME(6) NULL | 가입 시각으로 저장 (4.6) |
| privacy_agreed_at | DATETIME(6) NULL | 가입 시각으로 저장 (4.6) |
| created_at / updated_at / deleted_at | DATETIME(6) | 공통 |

그 밖의 컬럼(`profile_image_url`, `bio`, `failed_login_count`, `locked_until`, `notification_retention_days`, `withdrawn_at`)은 ERD에 있으니 V1에서 함께 만들되, 이 기능에서는 기본값만 쓴다. `failed_login_count`, `locked_until`은 SEC-03 후속 작업에서 쓴다.

**제약 (ERD 그대로)**: `ck_users_role`, `ck_users_status`, `ck_users_login_id`(USER면 login_id NULL), `ck_users_retention`.

**가입 시 검증 (애플리케이션)**:
- 이메일 형식, 길이 255자 이하
- 비밀번호 8~15자, 영문·숫자·특수문자 각각 1자 이상, 두 입력 일치 (SEC-02)
- 닉네임 정규식 `^[가-힣A-Za-z0-9]{2,12}$`, 예약어 `관리자`, `admin`, `운영자`, `탈퇴한 회원`(대소문자 무시) 불가
- 전화번호: 하이픈·공백 제거 후 `^01[0-9]{8,9}$`
- 두 필수 동의가 모두 true

## verification_codes (이메일 인증번호)

| 컬럼 | 타입 | 이 기능에서의 쓰임 |
|---|---|---|
| id | BIGINT PK | 가입 티켓의 인증 행 ID |
| email | VARCHAR(255) NOT NULL | 소문자로 저장 |
| purpose | VARCHAR(20) NOT NULL | 이 기능에서는 `SIGNUP`만 |
| code_hash | VARCHAR(100) NOT NULL | HMAC-SHA256(16진수 64자) |
| fail_count | TINYINT NOT NULL DEFAULT 0 | 5가 되면 무효 |
| expires_at | DATETIME(6) NOT NULL | 발송 + 10분 |
| verified_at | DATETIME(6) NULL | 인증 성공 시각 |
| used_at | DATETIME(6) NULL | 가입 완료 시각 |
| created_at | DATETIME(6) | 발송 시각. 1분 재발송 제한의 기준 |

**상태 전이**:

```text
발송됨 ──(맞는 번호, 10분 안, fail_count<5)──▶ 인증됨 ──(30분 안에 가입 완료)──▶ 사용됨
  │                                              │
  ├─(틀린 번호)─▶ fail_count+1 ─(5회)─▶ 무효       └─(30분 지남)─▶ 무효 (티켓 만료)
  ├─(10분 지남)─▶ 무효
  └─(같은 이메일로 재발송)─▶ 행 삭제 (새 행이 대신함)
```

- 같은 이메일·용도의 행 중 가장 최근 것만 유효하다.
- 재발송: 가장 최근 행의 `created_at`이 1분 이내면 거부한다.
- 이미 가입된 이메일도 행을 만든다(번호는 무작위, 메일은 안내문). 인증을 통과해도 가입 완료 단계에서 이메일 중복으로 일반 실패가 난다.
- 발송할 때 같은 이메일·용도의 이전 행을 지우고 새 행을 만든다. 가입을 마치면 `used_at`을 기록하고, 남은 만료·사용 행의 정리는 04:00 배치 후속 작업(4.5 "만료되거나 쓰면 바로 삭제")에서 한다.

## refresh_tokens (로그인 유지 정보)

| 컬럼 | 타입 | 이 기능에서의 쓰임 |
|---|---|---|
| id | BIGINT PK | Access Token의 `sid` |
| user_id | BIGINT NOT NULL, FK → users.id (ON DELETE CASCADE) | 회원 |
| token_hash | CHAR(64) NOT NULL, UNIQUE | Refresh Token의 SHA-256 |
| remember_me | BOOLEAN NOT NULL DEFAULT 0 | 로그인 유지 체크 여부 |
| last_activity_at | DATETIME(6) NOT NULL | 직접 한 행동의 마지막 시각 |
| expires_at | DATETIME(6) NOT NULL | 체크: 로그인 + 14일 고정. 미체크: last_activity_at + 30분 |
| revoked_at | DATETIME(6) NULL | 로그아웃 시각 |
| created_at | DATETIME(6) | 로그인 시각 |

**유효 조건**: `revoked_at IS NULL AND expires_at > now()` 그리고 회원 `status = 'ACTIVE'`.

**상태 전이**:

```text
로그인 ──▶ 유효 ──(로그아웃)──▶ 폐기
             │
             ├─(미체크, 직접 행동)─▶ last_activity_at, expires_at 갱신 (1분 간격)
             └─(expires_at 지남)─▶ 만료
```

- 한 회원이 기기마다 행을 하나씩 가진다. 로그아웃은 그 기기의 행만 폐기한다.
- 만료 행 삭제는 D-83 배치 후속 작업.

## 관계

```text
users 1 ──── N refresh_tokens
verification_codes (회원 없이 이메일로 묶음, FK 없음)
```

## 마이그레이션 메모

- 파일: `src/main/resources/db/migration/V1__create_member_auth_tables.sql`
- 내용: Crowfoot `export_ddl` 결과에서 `users`, `verification_codes`, `refresh_tokens`의 CREATE TABLE, `fk_refresh_tokens_users`, 세 테이블의 인덱스(`idx_users_name_phone`, `idx_verification_codes_*`, `idx_refresh_tokens_expires_at`)만 가져오고 테이블 옵션에 utf8mb4를 붙인다.
- `account_lookup_tokens`는 USR-08 후속 작업에서 별도 마이그레이션으로 추가한다.
