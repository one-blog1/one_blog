# Tasks: 블로그 참여 신청·승인

**Input**: [spec.md](spec.md), [plan.md](plan.md)

- [X] T001 V3 마이그레이션 `blog_join_requests` (ERD 그대로)
- [X] T002 [P] `BlogJoinRequest`, `JoinRequestStatus`, `BlogJoinRequestRepository`(행 잠금, 닉네임 포함 목록)
- [X] T003 [P] `BlogMember`에 부블로그장 권한·정지·떠난 시각 매핑, `rejoin()`, `canManageMembers()` 등
- [X] T004 `BlogRepository.addMemberCount`, `BlogMemberRepository.findByBlogIdAndUserId`
- [X] T005 [US1][US2] `BlogJoinService.apply/cancel/status` (회원 행 잠금, 7일 재신청)
- [X] T006 [US3] `BlogJoinService.pendingRequests/approve/reject` (신청 행 잠금, 권한 확인)
- [X] T007 `BlogJoinController`
- [X] T008 [P] 화면 `blog.html` 참여 영역·신청 목록, `js/blog-join.js`, `api.js`에 put·delete
- [X] T009 테스트 `BlogJoinIntegrationTest` (자유 참여, 승인·거절·취소, 7일, 권한, 비공개·일부 공개, 되살리기, 강제 퇴장, 관리자)
- [X] T010 `IntegrationTestSupport`가 모든 테이블을 비우도록 바꿈 (기능이 늘어도 테스트 정리 코드를 고치지 않게)
- [ ] T011 `./gradlew test` 통과 확인 (Mac 연결 후)
- [ ] T012 `v0.3.0` 태그 (테스트 통과·push 승인 후)
