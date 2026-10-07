# Data Model: 002 블로그 생성·목록·내 블로그

기준: Crowfoot ERD "One Blog 메인블로그" (https://crowfoot.java21.net/workspaces/58/models/655, 버전 6). 테이블·컬럼 이름, 타입, 제약은 ERD 그대로이며 이 문서에서 새로 설계하지 않는다 (constitution IV).

이 기능에서 만드는 테이블은 `blogs`, `blog_members`, `tags`, `blog_tags`, `files` 5개다. 마이그레이션 파일은 `V2__create_blog_tables.sql`이고, ERD에서 내보낸 DDL 중 이 5개 테이블과 그 사이(및 `users`로 가는) 외래 키·인덱스만 담는다. 모든 테이블은 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci`로 만든다.

**예외 하나 (로드맵 "002의 대표 이미지")**: `files.post_id`의 외래 키 `fk_files_posts`는 `posts` 테이블이 없어 이번에는 만들지 않고, 005에서 새 마이그레이션으로 더한다. 컬럼 `post_id`는 ERD대로 NULL 허용으로 만든다.

## blogs (블로그)

| 컬럼 | 타입 | 이 기능에서의 쓰임 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 블로그 ID |
| slug | VARCHAR(30) NULL, UNIQUE `uk_blogs_slug` | 블로그 주소. 정리 후 `^[a-z0-9]+(-[a-z0-9]+)*$`, 3~30자, 예약어 불가 (R2) |
| name | VARCHAR(50) NOT NULL | 앞뒤 공백 제거 후 1~50자, 중복 허용 |
| description | VARCHAR(500) NULL | 앞뒤 공백 제거 후 0~500자. 빈 값은 NULL |
| cover_image_url | VARCHAR(500) NULL | `/files/{stored_name}`. 없으면 NULL (화면이 기본 이미지) |
| visibility | VARCHAR(20) NOT NULL DEFAULT 'PUBLIC' | `PUBLIC`, `UNLISTED`, `PRIVATE` |
| join_policy | VARCHAR(20) NOT NULL DEFAULT 'OPEN' | `OPEN`, `APPROVAL` |
| share_token | CHAR(32) NULL, UNIQUE `uk_blogs_share_token` | `UNLISTED`일 때만 128비트 무작위 16진수 32자 (R6). 나머지는 NULL |
| status | VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' | 만들 때 `ACTIVE`. `CLOSING`·`CLOSED`는 012 |
| is_hidden | TINYINT(1) NOT NULL DEFAULT 0 | 목록에서 제외 조건으로만 읽음. 설정은 007 |
| close_scheduled_at, close_reason, closed_at | | 이번 기능에서 쓰지 않음 (012) |
| member_count | INT NOT NULL DEFAULT 1 | 만들 때 1 (블로그장). 인기순 기준 (D-89) |
| created_at / updated_at / deleted_at | DATETIME(6) | 공통 |

**제약 (ERD 그대로)**: `ck_blogs_visibility`, `ck_blogs_join_policy`, `ck_blogs_status`

**인덱스 (ERD 그대로)**: `idx_blogs_name_description`(FULLTEXT ngram, 검색 010에서 사용), `idx_blogs_visibility_status_created_at`, `idx_blogs_visibility_status_member_count`, `idx_blogs_close_scheduled_at`

**상태 흐름**: 이 기능에서는 `ACTIVE`로만 만든다. 이후 `ACTIVE ↔ CLOSING → CLOSED`(012).

## blog_members (블로그 멤버십)

| 컬럼 | 타입 | 이 기능에서의 쓰임 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | |
| user_id | BIGINT NOT NULL, FK `fk_blog_members_users` → users | 블로그를 만든 회원 |
| blog_id | BIGINT NOT NULL, FK `fk_blog_members_blogs` → blogs | |
| role | VARCHAR(20) NOT NULL DEFAULT 'MEMBER' | 만들 때 `OWNER` |
| can_edit_info, can_manage_members, can_manage_posts | TINYINT(1) NOT NULL DEFAULT 0 | 부블로그장 권한. 이번에는 기본값 |
| sub_owner_since | DATETIME(6) NULL | 쓰지 않음 |
| status | VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' | `ACTIVE`인 행만 멤버로 본다 |
| suspended_until | DATETIME(6) NULL | 쓰지 않음 (013) |
| joined_at / left_at / updated_at | DATETIME(6) | 만들 때 joined_at = 지금 |

**제약 (ERD 그대로)**: `uk_blog_members_blog_id_user_id`, `ck_blog_members_role`, `ck_blog_members_status`

**인덱스**: `idx_blog_members_user_id_status` (내 블로그, 개수 세기)

## tags (태그)

| 컬럼 | 타입 | 이 기능에서의 쓰임 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | |
| name | VARCHAR(30) NOT NULL, UNIQUE `uk_tags_name` | 정리 후 `^[가-힣a-z0-9_]{1,20}$` (R5). 글 태그(008)와 함께 씀 |
| created_at | DATETIME(6) | |

## blog_tags (블로그 태그)

| 컬럼 | 타입 | 이 기능에서의 쓰임 |
|---|---|---|
| blog_id | BIGINT NOT NULL, PK, FK `fk_blog_tags_blogs` → blogs ON DELETE CASCADE | |
| tag_id | BIGINT NOT NULL, PK, FK `fk_blog_tags_tags` → tags | |

**PK**: `(blog_id, tag_id)` — 한 블로그에 같은 태그가 두 번 들어가지 않는다. **인덱스**: `idx_blog_tags_tag_id` (태그 검색 010)

**검증 (애플리케이션)**: 블로그 하나에 0~10개 (BLG-01)

## files (업로드 파일)

| 컬럼 | 타입 | 이 기능에서의 쓰임 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 만들기 요청의 `coverFileId` |
| post_id | BIGINT NULL | 이번에는 항상 NULL. 외래 키는 005에서 추가 |
| user_id | BIGINT NOT NULL, FK `fk_files_users` → users | 올린 회원. 만들기 때 본인 파일인지 확인 |
| purpose | VARCHAR(20) NOT NULL | 이번에는 `BLOG_COVER` |
| stored_name | VARCHAR(64) NOT NULL, UNIQUE `uk_files_stored_name` | `UUID.randomUUID() + "." + 확장자` (최대 41자) |
| original_name | VARCHAR(255) NOT NULL | 원래 파일 이름(경로 부분 제거, 255자로 자름). 화면·저장 경로에 쓰지 않음 |
| content_type | VARCHAR(50) NOT NULL | 파일 앞부분으로 판단한 MIME 타입 |
| size_bytes | INT NOT NULL | 위치 정보를 지운 뒤 실제로 저장한 크기 |
| sort_order | SMALLINT NOT NULL DEFAULT 0 | 쓰지 않음 (글 이미지 005) |
| created_at / deleted_at | DATETIME(6) | |

**제약 (ERD 그대로)**: `uk_files_stored_name`, `ck_files_purpose`

## 관계 요약

```text
users 1 ─── N blog_members N ─── 1 blogs 1 ─── N blog_tags N ─── 1 tags
users 1 ─── N files            (blogs.cover_image_url은 files.stored_name을 경로로 가리킴, FK 없음 — ERD 그대로)
```

## 이 기능이 하는 쓰기 (한 트랜잭션)

블로그 만들기 (`BlogCreateService.create`):
1. `users` 행 `FOR UPDATE` 잠금 (R3)
2. 공개/비공개 개수 세기 → 초과면 중단
3. 주소 재검사, 태그 정리, 대표 이미지 파일 확인
4. `blogs` INSERT (`UNLISTED`면 share_token 포함)
5. `blog_members` INSERT (role `OWNER`)
6. `tags` INSERT IGNORE → `blog_tags` INSERT

이미지 올리기 (`FileUploadService.uploadBlogCover`): 디스크에 저장한 뒤 `files` INSERT. DB 저장이 실패하면 디스크 파일을 지운다.
