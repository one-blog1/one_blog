# Tasks: 팔로우·블로그 구독·프로필·회원정보 수정

- [X] T001 V9 `follows`, `blog_subscriptions`
- [X] T002 `User` 프로필 사진·소개 매핑, `RefreshTokenRepository.revokeOthers`/`revokeAll`
- [X] T003 `social`: `Follow`, `FollowRepository`, `FollowService`(토글·목록), `FollowController`, `FollowListener`
- [X] T004 `blog.subscription`: 구독 토글·내 구독 목록, `BlogAccessService` 구독자 허용(D-50), 블로그 정보에 `subscribed`, `subscriberCount`
- [X] T005 `profile`: `ProfileService`, `/users/{닉네임}` 화면·API
- [X] T006 `account`: 내 정보, 프로필 수정, 비밀번호 변경
- [X] T007 [P] 화면 `profile.html`, `account.html`, `blog-subscribe.js`, 내 블로그의 구독 목록, 상단 메뉴
- [X] T008 테스트 `SocialIntegrationTest`, `AccountIntegrationTest`
- [ ] T009 `./gradlew test` 통과 (Mac 연결 후)
