# Data Model: 소셜 간편 가입

DB 테이블 변경 없음. Crowfoot ERD "One Blog 메인블로그"의 `users`, `social_accounts`를 그대로 쓴다. 마이그레이션 없음 (다음 번호 V24는 쓰지 않음).

## 가입 대기 쿠키 (SOCIAL_PENDING, 서명한 JWT, 15분, path /api/auth)

| claim | 값 | 바뀜 |
|---|---|---|
| typ | `SOCIAL` | 그대로 |
| provider | KAKAO·NAVER·GOOGLE | 그대로 |
| pid | 사이트 회원 번호 | 그대로 |
| email | 사이트 이메일 (없을 수 있음) | 그대로 |
| ev | 사이트가 인증한 이메일인지 | 네이버는 이제 true |
| name | 이름 (형식 맞는 것만) | 네이버·구글 |
| nick | 닉네임 후보 | 그대로 |
| phone | 휴대전화 숫자 10~11자리 | **새로 추가** (네이버) |

`SocialTickets.Pending`에 `phone`을 더한다.

## 가입 화면 값 (`PendingView`, 서버가 만듦)

| 필드 | 뜻 |
|---|---|
| provider, providerLabel | 사이트 |
| email | 가린 이메일 (`ab***@naver.com`) |
| emailVerified | 사이트가 인증한 이메일인지. false면 이메일 칸 + 인증번호 |
| emailInUse | 같은 이메일 회원이 있음 (emailVerified일 때만 판단) |
| name | 가린 이름 또는 null |
| needName | 이름 칸을 보여 줄지 |
| phone | 가린 전화번호 또는 null |
| needPhone | 전화번호 칸을 보여 줄지 |
| nickname | 미리 채울 닉네임. 사이트 닉네임 그대로, 쓸 수 없을 때만 서버가 고친 값. 화면에서 고칠 수 있음 |

## 가입 요청 (`SocialSignupRequest`)

이름·전화번호는 쿠키에 있으면 무시하고, 없을 때만 필수. 닉네임·약관 동의 2개는 늘 필수 (지금 그대로).

## 회원 저장 (`User.createSocialMember`)

이메일·이름·닉네임·전화번호·약관 동의 시각 — 지금과 같다. 비밀번호 없음.
