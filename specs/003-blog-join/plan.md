# Implementation Plan: 블로그 참여 신청·승인

**Branch**: `main` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

## Summary

자유 참여 블로그는 신청 즉시 `blog_members`에 일반 멤버를 만들고, 승인제 블로그는 `blog_join_requests`에 대기 신청을 남겨 블로그장(멤버 관리 권한을 받은 부블로그장 포함)이 처리한다. 볼 수 있는지 판단은 002의 `BlogAccessService`를 그대로 쓰고, 같은 회원의 동시 신청은 회원 행 잠금(002 R3와 같은 방식), 같은 신청의 동시 처리는 신청 행 잠금으로 막는다. Flyway V3로 ERD의 `blog_join_requests`를 추가한다.

## Technical Context

001·002와 같다 (Spring Boot 4.1, Java 21, JPA, Flyway, MySQL 8.4, 정적 HTML + JS). 새 의존성 없음.

## Constitution Check

| 원칙 | 확인 | 결과 |
|---|---|---|
| I. 요구사항 ID 인용 | BLG-04·05, 6.5, SEC-07, D-01·71·90 | ✅ |
| II. 미룬 항목 기록 | 알림(011), 블랙리스트·차단·정지(013), 블로그 탈퇴(012) | ✅ |
| III. 역할을 매 요청 DB 확인 | `BlogMember.canManageMembers()`를 요청마다 DB 멤버십으로 확인 | ✅ |
| III. SQL 바인딩 | JPA·JPQL만 사용 | ✅ |
| III. textContent | 신청자 닉네임은 textContent | ✅ |
| III. 서버 마스킹 | 신청 목록은 닉네임만 내보냄 | ✅ |
| IV. ERD 기준·Flyway | V3에 ERD의 `blog_join_requests`(FK 2개, 인덱스 2개) 그대로 | ✅ |
| IV. 멤버십 테이블 | 멤버가 되면 `blog_members` 행 생성 또는 되살림 | ✅ |
| 권한 거부 테스트 | 비회원 401, 관리자·권한 없는 부블로그장·다른 회원 403 | ✅ |

## Design

### 상태 판단 (`BlogJoinService.currentStatus`)

1. 활성 멤버십이 있으면 `MEMBER`
2. 가장 최근 신청이 `PENDING`이면 `PENDING`
3. 가장 최근 신청이 `REJECTED`이고 처리 시각 + 7일이 지나지 않았으면 `REJECTED`(+ `retryAt`)
4. 그 밖은 `NONE`

### 동시성

- 신청: 회원 행 `SELECT ... FOR UPDATE` 후 상태 확인 → 같은 회원의 두 번째 요청은 첫 요청이 끝난 뒤 `PENDING`을 본다.
- 처리·취소: 신청 행 `SELECT ... FOR UPDATE` 후 `PENDING`인지 다시 확인.
- 멤버 수: `UPDATE blogs SET member_count = member_count + 1`로 DB에서 더한다.

### 멤버십 되살리기

`uk_blog_members_blog_id_user_id` 때문에 블로그·회원당 행이 하나다. `LEFT` 행이 있으면 `rejoin()`으로 `ACTIVE`·`MEMBER`·권한 없음으로 되돌린다. `KICKED`는 013의 블랙리스트 해제 전까지 막는다.

### API

| 메서드·주소 | 권한 | 응답 |
|---|---|---|
| `GET /api/blogs/{slug}/join?key=` | 로그인 + 블로그를 볼 수 있음 | `{status, retryAt?}` |
| `POST /api/blogs/{slug}/join?key=` | 같음, 관리자 제외 | 201 `{status:"MEMBER"}` 또는 202 `{status:"PENDING"}` |
| `DELETE /api/blogs/{slug}/join?key=` | 신청자 본인 | `{status:"NONE"}` |
| `GET /api/blogs/{slug}/join-requests` | 멤버 관리 권한 | `[{id, nickname, requestedAt}]` |
| `POST /api/blogs/{slug}/join-requests/{id}/approve` | 멤버 관리 권한 | 204 |
| `POST /api/blogs/{slug}/join-requests/{id}/reject` | 멤버 관리 권한 | 204 |

오류 코드: `ALREADY_MEMBER`(409), `JOIN_REQUEST_PENDING`(409), `REAPPLY_TOO_SOON`(409), `JOIN_BLOCKED`(403), `JOIN_REQUEST_NOT_FOUND`(404), `JOIN_REQUEST_ALREADY_PROCESSED`(409), `FORBIDDEN`(403), `ADMIN_NOT_ALLOWED`(403), 그리고 볼 수 없는 블로그는 002의 `PRIVATE_BLOG`·`LINK_REQUIRED`·`BLOG_NOT_FOUND`.

### 데이터 모델

`blog_join_requests` — ERD 그대로 (id, user_id FK, blog_id FK, status CHECK, processed_by_user_id(FK 없음), processed_at, created_at). 인덱스 `idx_blog_join_requests_blog_id_status`(블로그장 목록), `idx_blog_join_requests_user_id_blog_id_created_at`(내 최근 신청).

`blog_members` — 이 기능에서 부블로그장 권한(`can_*`), `sub_owner_since`, `suspended_until`, `left_at`을 엔티티에 매핑한다 (컬럼은 V2에 이미 있음).

### 화면

`blog.html`에 참여 영역(상태 문구, 참여하기/참여 신청/신청 취소 버튼)과 블로그장용 신청 목록(승인·거절)을 더한다. `js/blog-join.js`가 `blog.js`의 `blog:loaded` 이벤트를 받아 그린다.

## Project Structure

```text
src/main/java/com/oneblog/blog/join/   BlogJoinRequest, JoinRequestStatus, BlogJoinRequestRepository,
                                       BlogJoinService, BlogJoinController, JoinStatusResponse, JoinRequestItem
src/main/java/com/oneblog/blog/        BlogMember(권한 컬럼 매핑), BlogMemberRepository, BlogRepository(addMemberCount)
src/main/resources/db/migration/V3__create_blog_join_requests.sql
src/main/resources/static/             blog.html, js/blog-join.js, js/api.js(put·delete), css/app.css
src/test/java/com/oneblog/blog/join/BlogJoinIntegrationTest.java
src/test/java/com/oneblog/IntegrationTestSupport.java  (모든 테이블을 비우도록 바꿈)
```

## Complexity Tracking

없음.
