---
description: "Task list for 002 블로그 생성·목록·내 블로그"
---

# Tasks: 블로그 생성·목록·내 블로그

**Input**: Design documents from `/specs/002-blog-create-list/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/blog-api.md](contracts/blog-api.md), [quickstart.md](quickstart.md)

**Tests**: constitution 개발 흐름이 요구하는 "권한 없는 요청이 거부되는지" 테스트와, spec의 보안·규칙(개수 제한 동시 요청, 비공개 정보 비노출, 파일 형식 검사, 주소·태그 규칙)을 확인하는 테스트만 넣는다.

**Organization**: 사용자 스토리별로 나눠, 각 스토리를 따로 구현하고 시험할 수 있게 한다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일이고 아직 끝나지 않은 작업에 의존하지 않아 병렬로 할 수 있음
- **[Story]**: 사용자 스토리 (US1~US5)

## Path Conventions

- 애플리케이션 코드: `src/main/java/com/oneblog/`
- 설정·마이그레이션·화면: `src/main/resources/`
- 테스트: `src/test/java/com/oneblog/`

---

## Phase 1: Setup

- [X] T001 `src/main/resources/application.yml`에 `app.blog.limit.public=${BLOG_LIMIT_PUBLIC:3}`, `app.blog.limit.private=${BLOG_LIMIT_PRIVATE:5}`, `app.blog.reserved-slugs`(research R2 목록), `app.file.storage-dir=${FILE_STORAGE_DIR:./uploads}`, `spring.servlet.multipart.max-file-size=10MB`, `max-request-size=10MB`를 더한다
- [X] T002 [P] `.env.example`에 `FILE_STORAGE_DIR`, `BLOG_LIMIT_PUBLIC`, `BLOG_LIMIT_PRIVATE` 설명을 더한다 (값은 주석 처리, 기본값 있음)
- [X] T003 [P] `src/test/resources/application-test.yml`에 `app.file.storage-dir`를 테스트 전용 폴더(`build/test-uploads`)로 둔다

---

## Phase 2: Foundational

- [X] T004 `src/main/resources/db/migration/V2__create_blog_tables.sql`: ERD DDL에서 `blogs`, `blog_members`, `tags`, `blog_tags`, `files`의 CREATE TABLE(컬럼·COMMENT·CHECK 그대로, utf8mb4), 외래 키 `fk_blog_members_blogs`, `fk_blog_members_users`, `fk_blog_tags_blogs`(CASCADE), `fk_blog_tags_tags`, `fk_files_users`, 인덱스 `idx_blogs_name_description`(FULLTEXT ngram), `idx_blogs_visibility_status_created_at`, `idx_blogs_visibility_status_member_count`, `idx_blogs_close_scheduled_at`, `idx_blog_members_user_id_status`, `idx_blog_tags_tag_id`. `fk_files_posts`는 넣지 않는다 (data-model.md)
- [X] T005 [P] 블로그 엔티티와 열거형: `blog/Blog.java`, `BlogVisibility`, `BlogJoinPolicy`, `BlogStatus`, `BlogMember.java`, `BlogRole`, `BlogMemberStatus`
- [X] T006 [P] 리포지토리: `blog/BlogRepository.java`, `blog/BlogMemberRepository.java`, `member/UserRepository.findForUpdateById`(`@Lock(PESSIMISTIC_WRITE)`)
- [X] T007 [P] `blog/BlogProperties.java`(`app.blog`) — 공개 제한 1~5, 비공개 1 이상 검증
- [X] T008 [P] `tag/TagPolicy.java`(정리·검사) + `tag/TagService.java`(INSERT IGNORE 저장, 블로그별 태그 조회) (research R5)
- [X] T009 [P] `file/` 패키지: `StoredFile`, `FilePurpose`, `StoredFileRepository`, `FileStorage`, `LocalFileStorage`, `FileProperties`, `ImageTypeDetector`, `ImageMetadataStripper` (research R4)
- [X] T010 `common/web/GlobalExceptionHandler.java`에 `MaxUploadSizeExceededException` → 413 `FILE_TOO_LARGE`, `ApiException`에 추가 정보(`limitType`, `limit`)를 실을 수 있게 한다
- [X] T011 `common/config/SecurityConfig.java` 주소 규칙 (research R10)
- [X] T012 [P] `IntegrationTestSupport`: 블로그 테이블 정리, `loginCookie(email)`, `signUpAndLogin(email, nickname)` 도우미

---

## Phase 3: User Story 1 - 블로그 만들기 (P1) 🎯 MVP

- [X] T013 [P] [US1] `blog/BlogPolicy.java` 이름·소개·주소 정리와 검사, 예약어
- [X] T014 [P] [US1] `file/FileUploadService.java`, `file/FileController.java` (`POST /api/files/blog-cover`, `GET /files/{storedName}`)
- [X] T015 [US1] `blog/BlogCreateService.java` + `BlogApiController`의 `POST /api/blogs`, `GET /api/blog-slugs/availability`
- [X] T016 [US1] `blog/BlogPageController.java` `GET /blog/{slug}` → `forward:/blog.html`
- [X] T017 [P] [US1] 화면 `blog-new.html`, `js/blog-new.js`, `api.js`에 `upload()` 추가, `header.js`에 "블로그 만들기"·"내 블로그"
- [X] T018 [P] [US1] 테스트 `blog/BlogPolicyTest`, `tag/TagPolicyTest`, `file/ImageTypeDetectorTest`, `file/ImageMetadataStripperTest`
- [X] T019 [US1] 테스트 `blog/BlogCreateIntegrationTest`(만들기, 주소 규칙·중복, 태그 정리, 일부 공개 공유 링크, 비회원 401, 관리자 403, 남의 이미지 400, CSRF 없음 403), `file/FileUploadIntegrationTest`(형식·크기 거부, 받기)

## Phase 4: User Story 2 - 생성 개수 제한 (P1)

- [X] T020 [US2] `BlogCreateService`에 회원 행 잠금 + 개수 세기, `GET /api/me/blog-quota`
- [X] T021 [US2] 테스트 `blog/BlogLimitIntegrationTest`(공개 3·일부 공개 포함, 비공개 5, 동시 10건)

## Phase 5: User Story 3 - 메인 블로그 목록 (P2)

- [X] T022 [US3] `blog/BlogQueryService.java` 메인 목록 + `GET /api/blogs`
- [X] T023 [P] [US3] 화면 `index.html` 목록, `js/blog-card.js`, `js/blog-list.js`, `css/app.css`
- [X] T024 [US3] 테스트 `blog/BlogListIntegrationTest`(공개만, 정렬, 페이지·개수 보정)

## Phase 6: User Story 4 - 내 블로그 (P2)

- [X] T025 [US4] `BlogQueryService` 내 블로그 + `GET /api/me/blogs`
- [X] T026 [P] [US4] 화면 `my-blogs.html`, `js/my-blogs.js`
- [X] T027 [US4] 테스트(내 블로그 목록, 비회원 401)는 `BlogListIntegrationTest`에 함께 둔다

## Phase 7: User Story 5 - 블로그 첫 화면 접근 (P2)

- [X] T028 [US5] `blog/BlogAccessService.java` + `GET /api/blogs/{slug}`
- [X] T029 [P] [US5] 화면 `blog.html`(no-referrer), `js/blog.js`
- [X] T030 [US5] 테스트 `blog/BlogAccessIntegrationTest`(공개·일부 공개·비공개 × 비회원·다른 회원·블로그장, 틀린 key, 없는 주소, `/blog/{slug}` HTML)

## Phase 8: Polish

- [X] T031 `./gradlew test` 전체 통과 (82개, 001 포함). 화면은 API를 흉내 낸 브라우저 점검으로 목록·만들기·첫 화면·내 블로그·모바일 너비 확인
- [X] T032 [P] `docs/roadmap.md` 002 상태, readme 업로드 폴더 안내, 화면 접근 테스트 `blog/BlogPagesIntegrationTest`
- [ ] T033 실제 서버(`./gradlew bootRun`)로 quickstart.md 화면 시나리오 확인 (사용자)
- [ ] T034 `v0.2.0` 태그 (push 승인 후)

## Dependencies

- Phase 2 → 모든 스토리. US2는 US1의 `BlogCreateService`에 붙는다. US3·US4·US5는 US1이 만든 데이터가 있어야 시험할 수 있다.
