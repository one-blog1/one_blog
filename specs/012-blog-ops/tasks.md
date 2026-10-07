# Tasks: 블로그 운영, 회원탈퇴, 04:00 배치

- [X] T001 V12 `blog_transfer_requests`, `shedlock`. `Blog`(close_*), `BlogMember`(탈퇴·부블로그장·블로그장), `User`(탈퇴·개인정보 삭제) 메서드
- [X] T002 `BlogManageService`: 정보·공개 범위·참여 방식·공유 링크·멤버 목록·부블로그장·블로그 탈퇴
- [X] T003 `BlogTransferService`: 요청·취소·수락·거절·만료
- [X] T004 `BlogCloseService`, `BlogClock`, `BlogAudience`: 폐쇄·철회·3일/1일 전 알림·폐쇄 처리
- [X] T005 `WithdrawalService`: 회원탈퇴
- [X] T006 `batch`: `SchedulerLock`, `DailyBatch`, `DailyTask`, `PurgeTask`, `CleanupTask`, `BlogLifecycleTasks`
- [X] T007 [P] 화면 `blog-manage.html`, `blog-ops.js`(관리 링크·나가기·폐쇄 안내), 내 블로그의 위임 요청, 회원탈퇴
- [X] T008 테스트 `BlogOpsIntegrationTest`, `BlogClockTest`
- [ ] T009 `./gradlew test` 통과 (Mac 연결 후)
