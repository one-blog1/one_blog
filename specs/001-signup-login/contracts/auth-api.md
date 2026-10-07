# API Contract: 001 회원가입·로그인·로그아웃

모든 요청·응답 본문은 JSON(UTF-8)이다. 상태를 바꾸는 요청(POST)은 `X-XSRF-TOKEN` 헤더가 필요하다 (SEC-10). 사용자가 직접 한 행동으로 보내는 요청에는 화면이 `X-User-Activity: 1`을 붙인다 (D-62).

## 공통 오류 형식

```json
{
  "code": "VALIDATION_FAILED",
  "message": "입력값을 확인해 주세요.",
  "fieldErrors": [{ "field": "nickname", "message": "2~12자의 한글, 영문, 숫자만 쓸 수 있습니다." }]
}
```

`fieldErrors`는 입력값 오류일 때만 있다.

## 쿠키

| 이름 | 내용 | 속성 |
|---|---|---|
| `ACCESS_TOKEN` | JWT(10분), 클레임 `sub`, `sid` | HttpOnly, Secure, SameSite=Lax, Path=/ |
| `REFRESH_TOKEN` | 무작위 값 | HttpOnly, Secure, SameSite=Lax, Path=/api/auth. 로그인 유지 체크 시 Max-Age 14일, 아니면 세션 쿠키 |
| `SIGNUP_TICKET` | 가입 티켓 JWT(30분) | HttpOnly, Secure, SameSite=Strict, Path=/api/auth/signup, Max-Age 30분 |
| `XSRF-TOKEN` | CSRF 토큰 | JS가 읽을 수 있음, Path=/ |

`ACCESS_TOKEN`도 로그인 유지 여부에 따라 세션 쿠키 또는 Max-Age 14일로 맞춘다.

---

## POST /api/auth/signup/email-code — 인증번호 받기 (USR-02)

요청
```json
{ "email": "user@example.com" }
```

응답 `202 Accepted` — 가입 여부와 관계없이 같다 (D-28)
```json
{ "message": "인증번호를 보냈습니다.", "expiresInSeconds": 600, "resendAvailableInSeconds": 60 }
```

오류
| 상태 | code | 언제 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 이메일 형식이 틀림 |
| 429 | `RESEND_TOO_SOON` | 마지막 발송 후 1분이 안 됨. 본문에 `retryAfterSeconds` |

## POST /api/auth/signup/email-code/verify — 인증번호 확인 (USR-02)

요청
```json
{ "email": "user@example.com", "code": "123456" }
```

응답 `200 OK` + `SIGNUP_TICKET` 쿠키
```json
{ "verified": true, "signupExpiresInSeconds": 1800 }
```

오류
| 상태 | code | 언제 |
|---|---|---|
| 400 | `CODE_MISMATCH` | 번호가 틀림. 본문에 `remainingAttempts` |
| 410 | `CODE_EXPIRED` | 10분이 지났거나 5번 틀려 무효, 또는 발송 기록 없음 |

## GET /api/auth/signup/nickname-availability?nickname={닉네임} — 닉네임 확인 (6.5)

응답 `200 OK`
```json
{ "available": false, "reason": "TAKEN" }
```
`reason`: `TAKEN`, `RESERVED`, `INVALID_FORMAT`, 또는 사용 가능하면 생략. 최종 판단은 가입 완료 요청에서 다시 한다.

## POST /api/auth/signup — 가입 완료 (USR-01)

`SIGNUP_TICKET` 쿠키가 필요하다. 이메일은 티켓에서 읽고 본문으로 받지 않는다.

요청
```json
{
  "password": "Abcd123!",
  "passwordConfirm": "Abcd123!",
  "name": "홍길동",
  "nickname": "길동이",
  "phone": "010-1234-5678",
  "agreeTerms": true,
  "agreePrivacy": true
}
```

응답 `201 Created` + `SIGNUP_TICKET` 쿠키 삭제
```json
{ "message": "가입이 완료되었습니다. 로그인해 주세요." }
```

오류
| 상태 | code | 언제 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 비밀번호 규칙·일치, 이름·닉네임·전화번호 형식, 동의 누락 |
| 401 | `SIGNUP_TICKET_INVALID` | 티켓이 없거나 만료·위조, 인증 행이 이미 쓰였거나 없음 |
| 409 | `NICKNAME_UNAVAILABLE` | 닉네임 중복 또는 예약어 |
| 409 | `SIGNUP_FAILED` | 이메일이 이미 가입됨. 문구는 "가입을 완료할 수 없습니다. 처음부터 다시 시도해 주세요." (가입 여부를 드러내지 않음) |

## POST /api/auth/login — 로그인 (USR-03)

요청
```json
{ "email": "User@Example.com", "password": "Abcd123!", "rememberMe": false }
```

응답 `200 OK` + `ACCESS_TOKEN`, `REFRESH_TOKEN` 쿠키
```json
{ "nickname": "길동이" }
```

오류
| 상태 | code | 언제 |
|---|---|---|
| 401 | `LOGIN_FAILED` | 이메일 없음, 비밀번호 틀림, 탈퇴 등 비활성 계정 모두 같은 문구 "이메일 또는 비밀번호가 올바르지 않습니다." |

## POST /api/auth/refresh — Access Token 다시 받기 (SEC-04)

`REFRESH_TOKEN` 쿠키가 필요하다. `api.js`가 `401 TOKEN_EXPIRED`를 받으면 자동으로 한 번 호출한다.

응답 `204 No Content` + 새 `ACCESS_TOKEN` 쿠키

오류
| 상태 | code | 언제 |
|---|---|---|
| 401 | `SESSION_EXPIRED` | Refresh Token이 없거나 폐기·만료(30분 무활동 포함), 회원 비활성. 쿠키를 모두 지운다 |

## POST /api/auth/logout — 로그아웃 (USR-04)

응답 `204 No Content` + `ACCESS_TOKEN`, `REFRESH_TOKEN` 쿠키 삭제. 그 기기의 `refresh_tokens` 행에 `revoked_at`을 기록한다. 이미 로그아웃된 상태여도 204를 준다.

## GET /api/me — 내 로그인 정보

로그인 상태 확인과 상단 메뉴 표시에 쓴다. `X-User-Activity` 헤더가 없는 자동 요청이다.

응답 `200 OK`
```json
{ "id": 1, "nickname": "길동이", "role": "USER" }
```

오류
| 상태 | code | 언제 |
|---|---|---|
| 401 | `TOKEN_EXPIRED` | Access Token 만료 (refresh로 다시 받을 수 있음) |
| 401 | `UNAUTHENTICATED` | 로그인 안 함, 로그인이 폐기·만료됨 |

## 인증 필터 동작 (모든 보호된 요청)

1. `ACCESS_TOKEN` 서명·만료 확인. 만료면 `401 TOKEN_EXPIRED`.
2. `sid`로 refresh_tokens 행, `sub`로 users 행을 읽는다. 행이 폐기·만료됐거나 회원이 ACTIVE가 아니면 `401 UNAUTHENTICATED`.
3. `X-User-Activity: 1`이고 로그인 유지 미체크이며 `last_activity_at`이 1분 이상 지났으면 `last_activity_at = now`, `expires_at = now + 30분`으로 갱신한다.

## 화면 (정적 HTML)

| 경로 | 내용 |
|---|---|
| `/` | 메인. 상단 메뉴에 로그인 상태(닉네임/로그인·회원가입 버튼, 로그아웃) |
| `/signup.html` | 가입 ①~③ 단계 |
| `/login.html` | 이메일, 비밀번호, 로그인 유지 체크박스 |
