# API 계약: 소셜 간편 가입

새 주소는 없다. 아래 세 API의 필드만 바뀐다. 모두 가입 대기 쿠키(SOCIAL_PENDING)가 있어야 하고, 없거나 지나면 `401 SOCIAL_PENDING_EXPIRED`.

## GET /api/auth/social/pending

```json
{
  "provider": "NAVER",
  "providerLabel": "네이버",
  "email": "ab***@naver.com",
  "emailVerified": true,
  "emailInUse": false,
  "name": "홍*동",
  "needName": false,
  "phone": "010-****-5678",
  "needPhone": false,
  "nickname": "길동"
}
```

- 카카오: `name: null, needName: true, phone: null, needPhone: true`
- 구글: `needPhone: true`
- `emailVerified: false`(카카오·구글이 인증 표시를 안 준 경우): 화면은 이메일 칸 + 인증번호 (지금 흐름)

## POST /api/auth/signup/social

```json
{ "name": "홍길동", "phone": "010-1234-5678", "nickname": "길동", "agreeTerms": true, "agreePrivacy": true }
```

- `name`, `phone`: 쿠키에 값이 있으면 무시. 없으면 필수 (형식 오류 `400` 필드 오류)
- `nickname`, `agreeTerms`, `agreePrivacy`: 늘 필수
- 성공 `201 {"nickname": "..."}` + 로그인 쿠키, 가입 대기 쿠키 삭제
- 오류: `409 EMAIL_IN_USE`, `409 NICKNAME_UNAVAILABLE`, `409 SOCIAL_ALREADY_LINKED`, `401 EMAIL_NOT_VERIFIED` (지금 그대로)

## POST /api/auth/social/link

```json
{ "password": "...", "captchaToken": "..." }
```

- `email`을 빼면 쿠키의 이메일을 쓴다 (사이트가 인증한 이메일일 때만). 넣으면 지금처럼 그 이메일
- 성공 `200 {"nickname": "..."}` + 로그인 쿠키, 소셜 계정 연결
- 로그인 실패·잠금·Turnstile 응답은 일반 로그인과 같다
