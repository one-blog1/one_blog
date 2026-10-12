# Tasks: 소셜 로그인

- [X] T001 ERD: REQ-034(USR-09) + social_accounts (Crowfoot v18)
- [X] T002 `V19__create_social_accounts.sql`
- [X] T003 의존성 spring-security-oauth2-client, application.yml app.social.*
- [X] T004 `auth/social/*` (등록, 쿠키 저장소, 토큰 저장 안 함, 사용자 정보 맞추기, 티켓, 처리기, 서비스, API)
- [X] T005 `SecurityConfig` oauth2Login·허용 주소, `AuthService` 나누기, 재확인·탈퇴 비밀번호 없는 회원 처리, 탈퇴 때 연결 삭제
- [X] T006 화면: 로그인·가입 단추, 소셜 가입 화면, 내 정보 연결 칸, app.css
- [X] T007 처리방침, deploy.yml, .env.example, DEPLOY.md
- [X] T008 테스트 `SocialLoginIntegrationTest`, `SocialProfilesTest`
- [ ] T009 CI `./gradlew test` 통과
- [ ] T010 배포 뒤 세 사이트 실제 로그인 확인 (구글은 키 등록 후)
- [ ] T011 기능을 마치면 이 폴더를 문서 저장소 `specs/`로 옮김
