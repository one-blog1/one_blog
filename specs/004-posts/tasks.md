# Tasks: 글 작성·조회·수정·삭제, 목록

- [X] T001 V4 마이그레이션 `posts` (ERD, `fk_posts_categories` 제외)
- [X] T002 [P] commonmark·jsoup 의존성, `MarkdownRenderer`
- [X] T003 [P] `Post`, `PostType`, `DeletedBy`, `PostRepository`
- [X] T004 [P] `PageParams`, `Times`, `UserDisplayService`(탈퇴한 회원·탈퇴한 계정)
- [X] T005 `PostService`(쓰기·고치기·지우기·상세·목록), `PostExtension` 자리, `PostController`
- [X] T006 `BlogAccessService.check(Blog, …)`, `activeMembership`
- [X] T007 화면 라우트 `/blog/{slug}/posts/{id}`, `/write`, `/edit`, SecurityConfig 주소 규칙
- [X] T008 [P] 화면 `post.html`, `post-edit.html`, 블로그 첫 화면 글 목록, `session-keeper.js`(D-62), 임시저장
- [X] T009 테스트 `PostIntegrationTest` (쓰기 권한, 길이, XSS, 고치기·지우기 권한, 탈퇴한 계정, 공지·목록, 비공개)
- [X] T010 `./gradlew test` 통과 (182개, 2026-10-08)
- [ ] T011 `v0.4.0` 태그 (테스트 통과·push 승인 후)
