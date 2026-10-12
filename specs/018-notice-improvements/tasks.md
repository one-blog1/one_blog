# Tasks: 메인 공지 개선

- [X] T001 Crowfoot ERD v11: REQ-025(BRD-10) 규칙 보강, `posts.notice_category`, `ck_posts_notice_category`
- [X] T002 `V18__add_post_notice_category.sql` (기존 메인 공지는 GENERAL로 채움)
- [X] T003 `NoticeCategory` enum, `Post.noticeCategory`
- [X] T004 `NoticeService` 작성 시 종류, `update`·`source`, 목록 종류 거르기, 상세 `edited`·`updatedAt`
- [X] T005 `GET /api/notices?category=`, `GET·PUT /api/admin/notices/{id}`
- [X] T006 [P] `notice.html`·`notice.js` 목록(종류 탭·페이지)과 내용(목록으로 돌아가기)
- [X] T007 [P] `index.html`·`notice-strip.js` 2초마다 넘어가는 한 줄, 멈춤, 움직임 줄이기
- [X] T008 [P] `admin.html`·`admin.js` 종류 고르기, [수정](삭제 왼쪽)·수정 저장·취소, 제목 링크
- [X] T009 `app.css` 관리자·공지(007) 자리에 공지 줄·종류 이름표·목록
- [X] T010 테스트 `AdminIntegrationTest`: 종류 거르기·잘못된 종류 400, 수정·활동 기록·알림 없음·수정됨, 회원 403·비회원 401·지운 공지 404
- [X] T011 화면 확인(헤드리스 브라우저 29개 항목: 2초 전환·멈춤·목록 거르기·수정 흐름·휴대폰·움직임 줄이기)
- [X] T012 CI `./gradlew test` 통과 (wip 브랜치에서 실행, run 38204309523)
- [ ] T013 기능을 마치면 이 폴더를 문서 저장소 `specs/`로 옮김
