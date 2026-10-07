# Quickstart: 001 회원가입·로그인·로그아웃 검증

## 준비

- JDK 21, MySQL 8.4 실행 중
- DB와 계정 (한 번만)
  ```sql
  CREATE DATABASE one_blog CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
  CREATE DATABASE one_blog_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
  CREATE USER 'one_blog'@'localhost' IDENTIFIED BY '<비밀번호>';
  GRANT ALL PRIVILEGES ON one_blog.* TO 'one_blog'@'localhost';
  GRANT ALL PRIVILEGES ON one_blog_test.* TO 'one_blog'@'localhost';
  ```
- 환경변수 (저장소에 올리지 않음, constitution III)
  ```bash
  export DB_USERNAME=one_blog
  export DB_PASSWORD='<비밀번호>'
  export JWT_SECRET='<32바이트 이상 무작위 문자열>'
  export CODE_PEPPER='<무작위 문자열>'
  # 실제 메일을 보낼 때만
  export MAIL_USERNAME='<gmail 주소>'
  export MAIL_PASSWORD='<gmail 앱 비밀번호>'
  ```
  무작위 값 만들기: `openssl rand -base64 48`

## 실행

```bash
./gradlew test                                   # 단위·통합 테스트 (one_blog_test DB 사용)
./gradlew bootRun --args='--spring.profiles.active=local'
```

- `local` 프로필은 `app.mail.mode=log`라 인증번호가 메일 대신 서버 로그에 찍힌다. 실제 메일을 보내려면 `MAIL_*`를 설정하고 `--app.mail.mode=smtp`를 더한다.
- 로컬은 `http://localhost:8080`에서 Secure 쿠키를 쓸 수 있도록 local 프로필에서만 `app.cookie.secure=false`로 둔다.
- 첫 실행 때 Flyway가 `V1__create_member_auth_tables.sql`을 적용한다. 확인: `SELECT version, success FROM flyway_schema_history;`

## 검증 시나리오

API 상세는 [contracts/auth-api.md](contracts/auth-api.md), 테이블은 [data-model.md](data-model.md)를 본다.

| # | 시나리오 | 기대 결과 | 스펙 |
|---|---|---|---|
| 1 | `/signup.html`에서 새 이메일로 인증번호 받기 → 로그의 번호 입력 → 비밀번호 → 정보·동의 → 가입 | 로그인 화면으로 이동, `users`에 행 1개, `password_hash`가 `$2a$`로 시작 | US1-1,3,7,8 / SC-006 |
| 2 | 1번의 이메일로 다시 인증번호 받기 | 화면 문구·응답 본문이 1번과 같고, 로그에 "이미 가입된 이메일" 안내 메일 기록 | US1-2 / SC-004 |
| 3 | 인증번호를 5번 틀리게 입력 | 4번까지 남은 횟수 표시, 5번째에 무효 안내 | US1-4 |
| 4 | 인증번호 받은 직후 다시 보내기 | `429 RESEND_TOO_SOON`, 남은 초 표시 | US1-5 |
| 5 | 인증 없이 `POST /api/auth/signup` 직접 호출 | `401 SIGNUP_TICKET_INVALID` | FR-006 |
| 6 | 닉네임 `Admin` 또는 이미 있는 닉네임으로 가입 | `409 NICKNAME_UNAVAILABLE` | US1-9 |
| 7 | `User@Example.COM`처럼 대소문자를 바꿔 로그인 | 로그인 성공, 상단에 닉네임 | US2-1,2 |
| 8 | 비밀번호를 틀리게 / 없는 이메일로 로그인 | 두 경우 같은 문구 | US2-3 |
| 9 | 로그인 후 로그아웃 → 브라우저 개발자 도구로 이전 `ACCESS_TOKEN` 값을 넣고 `/api/me` 호출 | `401 UNAUTHENTICATED` | US2-4 / SC-005 |
| 10 | 로그인 유지 미체크 로그인 → DB에서 `expires_at`을 과거로 바꿈 → 페이지 이동 | 로그인 화면으로 이동 | US3-2 / SC-007 |
| 11 | 로그인 유지 체크 로그인 → 브라우저 재시작 | 로그인 유지, `refresh_tokens.expires_at`이 로그인 + 14일 | US3-5 |
| 12 | `X-XSRF-TOKEN` 없이 `POST /api/auth/login` 호출 | `403` | FR-022 |
| 13 | 닉네임에 `<script>` 같은 값을 넣어 가입 시도 | 형식 오류로 거부. 화면에 글자 그대로 표시 | FR-023 |
| 14 | 권한 없는 접근: 로그인하지 않고 `/api/me` 호출 | `401 UNAUTHENTICATED` | constitution 개발 흐름(권한 거부 테스트) |
