# Implementation Plan: 메인 관리자 1차

**Branch**: `main` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

## Summary

관리자 로그인은 001의 로그인 흐름(토큰·쿠키)을 그대로 쓰고 아이디만 `users.login_id`로 찾는다. 회원 로그인은 관리자 계정을 거부한다. `/api/admin/**`는 `hasRole("ADMIN")`. 목록 조회는 바인딩 파라미터를 쓰는 SQL(NamedParameterJdbcTemplate)로, 조치는 JPA 엔티티로 바꾸고 `AdminActionLogger`가 같은 트랜잭션에 기록을 남긴다. 메인 공지는 `posts`(MAIN_NOTICE, blog_id NULL). Flyway V7로 ERD의 `admin_actions`를 추가한다.

## Research

### R1. 처음 관리자 계정 (D-94)

- **Decision**: `ADMIN_LOGIN_ID`, `ADMIN_PASSWORD` 환경변수가 있으면 서버가 시작할 때 없는 경우에만 만든다(`AdminAccountInitializer`). 비밀번호는 SEC-02 규칙, bcrypt.
- **Rationale**: 운영자가 DB에 직접 해시를 넣지 않아도 되고, 비밀번호가 저장소에 남지 않는다 (constitution III).

### R2. 숨긴 블로그 접근

- **Decision**: 숨긴 블로그는 멤버가 아니면 404(없는 블로그)로 처리한다.
- **Rationale**: "숨김"이 공개 범위보다 강하게 작동해야 하고, 존재 여부도 드러내지 않는다.

### R3. 관리자 IP

- **Decision**: `request.getRemoteAddr()`. 로드밸런서를 붙일 때 프록시 헤더를 읽도록 바꾼다 (4.3).

## Constitution Check

| 원칙 | 확인 | 결과 |
|---|---|---|
| III. 서버 마스킹 | 목록·상세는 `Masking`으로 가린 값만 | ✅ |
| III. SQL 바인딩 | 검색어는 이스케이프 후 바인딩(`LIKE :q`) | ✅ |
| III. 비밀값 | 관리자 비밀번호는 환경변수만 | ✅ |
| IV. ERD·Flyway | V7 `admin_actions` | ✅ |
| 권한 거부 테스트 | 비회원 401, 회원 403, 관리자의 회원 로그인·회원의 관리자 로그인 거부 | ✅ |

## API (모두 `/api/admin/**`는 ADMIN)

| 메서드·주소 | 설명 |
|---|---|
| `POST /api/auth/admin/login` | `{loginId, password, rememberMe?}` |
| `GET /api/admin/users?q&page&size`, `GET /api/admin/users/{id}`, `POST /api/admin/users/{id}/reveal` | 회원 |
| `GET /api/admin/blogs?q`, `POST /api/admin/blogs/{id}/hide` `{hidden, reason}` | 블로그 |
| `GET /api/admin/posts?q`, `POST /api/admin/posts/{id}/hide`, `POST /api/admin/posts/{id}/delete` | 글 |
| `GET /api/admin/comments?q`, `POST /api/admin/comments/{id}/hide`, `POST /api/admin/comments/{id}/delete` | 댓글 |
| `GET /api/admin/stats?days`, `GET /api/admin/actions` | 통계, 기록 |
| `POST /api/admin/notices`, `DELETE /api/admin/notices/{id}`, `GET /api/notices`, `GET /api/notices/{id}` | 공지 |

## Project Structure

```text
src/main/resources/db/migration/V7__create_admin_actions.sql
src/main/java/com/oneblog/admin/   AdminService, AdminController, AdminAction(Repository), AdminActionLogger,
                                   AdminAccountInitializer, AdminProperties, NoticeService, NoticeController,
                                   NoticeRepository, AdminListener, NoticeListener, AdminPage, AdminQueries
src/main/java/com/oneblog/common/text/Masking.java
src/main/java/com/oneblog/auth/    AuthService(adminLogin), AuthController
src/main/resources/static/         admin-login.html, admin.html, js/admin.js, notice.html, js/notice-strip.js
src/test/java/com/oneblog/admin/AdminIntegrationTest.java, common/MaskingTest.java
```
