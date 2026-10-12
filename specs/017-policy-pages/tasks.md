# Tasks: 개인정보 처리방침·이용약관 화면

- [X] T001 요구사항 4.4~4.6, 3.7, USR-01·05, 개인정보보호위원회 작성지침 항목 확인
- [X] T002 [P] `privacy.html` 개인정보 처리방침 (법 30조 필수 항목, 국외 이전 표)
- [X] T003 [P] `terms.html` 이용약관 (금지 행위, 게시물, 이용 제한, 탈퇴)
- [X] T004 `header.js` `renderFooter()` 모든 화면 하단 링크, `app.css` `.site-footer`·`.policy`
- [X] T005 `SecurityConfig` 비회원 허용 목록에 `/privacy.html`, `/terms.html`
- [X] T006 `signup.html`·`signup.js` 전체 동의, 보기 링크, 두 필수 동의 전 가입 버튼 잠금
- [X] T007 테스트 `PolicyPagesIntegrationTest`, `SignupIntegrationTest` 이용약관 미동의 400
- [ ] T008 `./gradlew test` 통과, 화면 확인
- [ ] T009 운영자 이름, 보호책임자 이름·연락처 채우기
