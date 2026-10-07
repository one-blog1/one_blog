# Tasks: 제재·블랙리스트·신고·차단

- [X] T001 V13 `blog_blacklists`, `blacklist_inquiries`, `sanctions`, `reports`, `blocks`
- [X] T002 `block`: 차단 토글·목록, `BlockSearchFilter`, `BlockHiddenAuthors`, `BlockFollowGate`, `BlockJoinGate`
- [X] T003 `JoinGate`/`FollowGate`/`HiddenAuthors` 자리를 참여·팔로우·글 목록·댓글에 연결, 태그 목록에 `SearchFilter`
- [X] T004 `BlogMember` 정지·강제 퇴장, `BlogAccessService` 정지 안내(기간·사유)
- [X] T005 `sanction`: `SanctionService`(경고·정지·해제·강제 퇴장), `BlacklistService`(등록·검사·문의·처리), `PrivacyHasher`
- [X] T006 `OwnerSanctionService`: 블로그장 경고(3회 → 박탈)·박탈(승계/폐쇄 예약)·강제 폐쇄
- [X] T007 `report`: 신고 접수(접수처·2주·본인 제외·스냅숏), 블로그장 처리, 관리자 처리
- [X] T008 04:00 배치 `SanctionCleanupTask`(폐쇄 블로그 블랙리스트, 1년 지난 신고·제재)
- [X] T009 [P] 화면: 신고 창(`report.js`), 프로필 차단·신고, 블로그 신고·해제 문의·정지 안내, 관리 화면 신고·제재·블랙리스트, 관리자 신고 탭·블로그장 제재
- [X] T010 테스트 `ModerationIntegrationTest`
- [ ] T011 `./gradlew test` 통과 (Mac 연결 후)
