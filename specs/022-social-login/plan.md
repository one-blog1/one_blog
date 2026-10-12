# Implementation Plan: 소셜 로그인

## 구조

- `auth/social/SocialClientRegistrations`: 키 있는 사이트만 ClientRegistration. 카카오 scope profile_nickname(비즈 앱 아님 → 이메일 없음), 네이버는 PKCE 끔(requireProofKey false), 구글은 openid 없이 profile·email + userinfo v3. 시크릿이 없으면 NONE(PKCE만).
- `SocialAuthorizationRequestCookies`: AuthorizationRequestRepository 쿠키 구현 (JWT 서명, 필드를 클레임으로).
- `NoTokenStorage`: OAuth2AuthorizedClientRepository 아무것도 안 함 (기본은 메모리에 쌓음 → SCL-01).
- `SocialProfiles`: 카카오 {id, kakao_account}, 네이버 {response}, 구글 {sub, email_verified} → Pending.
- `SocialTickets`: 가입 대기(typ SOCIAL)·연결 표시(typ SOCIAL_LINK) JWT. typ이 ACCESS가 아니라 로그인 쿠키로 못 쓴다.
- `SocialLoginHandlers`: 성공 → 연결 표시가 있으면 연결, 아니면 로그인 또는 가입 대기. 실패 → social_error.
- `SocialLoginService`: 로그인·가입·비밀번호 연결·내 연결 목록·끊기. `AuthService.authenticateMember/issueLogin`을 나눠 비밀번호 확인·로그인 발급을 같이 쓴다.
- `SecurityConfig.oauth2Login`: loginPage /login.html(기본 로그인 화면 끔), 위 저장소·처리기.
- 재확인·탈퇴: `ReauthService.requirePassword`. 탈퇴 때 social_accounts 삭제.

## 화면

- `js/components/social-login.js`: login.html·signup.html 단추 (사이트 색 알약, 로고 그림 없음).
- `social-signup.html` + `js/pages/social-signup.js`: 이메일(필요하면 인증번호) → 정보·동의 → 가입, 아래 비밀번호 연결.
- `account.html` 소셜 로그인 칸 + `js/pages/account/social.js` (account.js도 `js/pages/account/`로 옮김).

## 배포

- deploy.yml: 여섯 키를 deploy.env로, PUBLIC_BASE_URL 기본 https://one-blog.java21.net.
- 사이트 개발자 화면 Redirect URI: `https://one-blog.java21.net/login/oauth2/code/{kakao|naver|google}`, 로컬 `http://localhost:8080/...`.
