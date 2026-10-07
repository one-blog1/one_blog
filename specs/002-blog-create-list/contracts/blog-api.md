# API Contract: 002 블로그 생성·목록·내 블로그

001의 규칙([auth-api.md](../../001-signup-login/contracts/auth-api.md))을 그대로 따른다: 본문은 JSON(UTF-8), 상태를 바꾸는 요청(POST)은 `X-XSRF-TOKEN` 헤더 필요(SEC-10), 사용자가 직접 한 행동에는 `X-User-Activity: 1`(D-62), 오류는 공통 오류 형식(`code`, `message`, `fieldErrors`).

로그인이 필요한 API에 로그인 없이 요청하면 `401 UNAUTHENTICATED`다(001과 같음).

공통 값

| 값 | 뜻 |
|---|---|
| `visibility` | `PUBLIC`(공개), `UNLISTED`(일부 공개), `PRIVATE`(비공개) |
| `joinPolicy` | `OPEN`(자유), `APPROVAL`(승인제) |
| `role` | `OWNER`(블로그장), `SUB_OWNER`(부블로그장), `MEMBER` |

---

## POST /api/files/blog-cover — 대표 이미지 올리기 (6.3, SEC-08)

로그인 필요. `multipart/form-data`, 필드 `file` 1개.

응답 `201 Created`
```json
{ "fileId": 12, "url": "/files/3f2a9c1e-8b7d-4e6f-9a0b-1c2d3e4f5a6b.jpg" }
```

오류
| 상태 | code | 언제 |
|---|---|---|
| 400 | `UNSUPPORTED_FILE_TYPE` | 확장자·MIME 타입·파일 앞부분 중 하나라도 jpg·jpeg·png·gif·webp가 아니거나 서로 다름 |
| 400 | `VALIDATION_FAILED` | `file`이 없거나 비어 있음 |
| 413 | `FILE_TOO_LARGE` | 3MB 초과 |
| 403 | `ADMIN_NOT_ALLOWED` | 관리자 계정 (D-90) |

## GET /files/{storedName} — 올린 파일 받기

로그인 불필요. 이미지 바이트를 저장된 MIME 타입으로 돌려준다. 헤더 `X-Content-Type-Options: nosniff`, `Content-Disposition: inline`, `Cache-Control: public, max-age=86400`. 이름 형식이 틀리거나 없거나 삭제된 파일은 `404`.

---

## GET /api/blog-slugs/availability?slug={주소} — 주소 확인 (6.5)

로그인 필요. 입력은 대소문자·앞뒤 공백을 정리해서 판단한다.

응답 `200 OK`
```json
{ "slug": "my-trip", "available": true }
```
```json
{ "slug": "admin", "available": false, "reason": "RESERVED" }
```
`reason`: `INVALID_FORMAT`(영문 소문자·숫자·- 3~30자 아님), `RESERVED`(예약어), `TAKEN`(이미 쓰임)

## GET /api/me/blog-quota — 남은 생성 개수 (BLG-10)

로그인 필요.

응답 `200 OK`
```json
{
  "public":  { "used": 2, "limit": 3, "remaining": 1 },
  "private": { "used": 0, "limit": 5, "remaining": 5 }
}
```
`public`은 공개 + 일부 공개를 합친 수다 (D-51). 관리자 계정은 `403 ADMIN_NOT_ALLOWED`.

---

## POST /api/blogs — 블로그 만들기 (BLG-01, BLG-10)

로그인 필요.

요청
```json
{
  "name": "제주 한 달 살기",
  "slug": "jeju-month",
  "description": "제주에서 한 달 지낸 기록",
  "coverFileId": 12,
  "tags": ["#여행", "제주 맛집", "Jeju"],
  "visibility": "UNLISTED",
  "joinPolicy": "APPROVAL"
}
```
- 필수: `name`, `slug`, `visibility`, `joinPolicy`. `description`, `coverFileId`, `tags`는 생략 가능.
- `tags`는 서버가 정리한다: `["여행", "제주_맛집", "jeju"]`.

응답 `201 Created`
```json
{
  "id": 7,
  "slug": "jeju-month",
  "url": "/blog/jeju-month",
  "shareUrl": "/blog/jeju-month?key=9f86d081884c7d659a2feaa0c55ad015"
}
```
`shareUrl`은 `UNLISTED`일 때만 있다.

오류
| 상태 | code | 언제 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 이름 1~50자 아님, 소개 500자 초과, 공개 범위·참여 방식 값이 틀림, 태그 형식이 틀리거나 11개 이상(`fieldErrors`의 `field`는 `name`, `tags`, `tags[2]` 등) |
| 400 | `INVALID_SLUG` | 주소 형식이 틀림 |
| 400 | `RESERVED_SLUG` | 예약어 주소 |
| 409 | `SLUG_TAKEN` | 이미 쓰이는 주소 (동시에 만든 경우 포함) |
| 400 | `INVALID_COVER_FILE` | `coverFileId`가 없거나, 내가 올린 대표 이미지가 아니거나, 삭제됨 |
| 409 | `BLOG_LIMIT_EXCEEDED` | 개수 제한 초과. 본문에 `"limitType": "PUBLIC"` 또는 `"PRIVATE"`와 `"limit": 3` |
| 403 | `ADMIN_NOT_ALLOWED` | 관리자 계정 (D-90) |

---

## GET /api/blogs?sort=latest&page=1&size=10 — 블로그 목록 (BLG-02)

로그인 불필요. 공개(`PUBLIC`)이고 폐쇄·숨김·삭제되지 않은 블로그만.

- `sort`: `latest`(기본, 최신순) | `popular`(멤버 수 많은 순, 같으면 최신순)
- `page`: 1부터. 범위를 벗어나면 1
- `size`: 10(기본) | 20 | 30. 그 밖의 값은 10

응답 `200 OK`
```json
{
  "items": [
    {
      "slug": "jeju-month",
      "name": "제주 한 달 살기",
      "description": "제주에서 한 달 지낸 기록",
      "coverImageUrl": "/files/3f2a9c1e-8b7d-4e6f-9a0b-1c2d3e4f5a6b.jpg",
      "tags": ["여행", "제주_맛집", "jeju"],
      "memberCount": 1,
      "ownerNickname": "제주사람",
      "createdAt": "2026-10-07T17:30:00+09:00"
    }
  ],
  "sort": "latest",
  "page": 1,
  "size": 10,
  "totalItems": 1,
  "totalPages": 1
}
```
`coverImageUrl`은 없으면 `null`. 소개를 줄이는 것은 화면이 한다.

## GET /api/me/blogs — 내 블로그 목록 (BLG-06)

로그인 필요.

응답 `200 OK`
```json
{
  "owned": [
    { "slug": "jeju-month", "name": "제주 한 달 살기", "coverImageUrl": null, "visibility": "UNLISTED", "role": "OWNER", "memberCount": 1, "createdAt": "2026-10-07T17:30:00+09:00" }
  ],
  "joined": []
}
```
`owned`는 블로그장인 블로그, `joined`는 부블로그장·멤버인 블로그. 둘 다 만든(참여한) 순서의 역순. 폐쇄된 블로그는 빠진다.

---

## GET /api/blogs/{slug}?key={공유 링크 값} — 블로그 첫 화면 정보 (BLG-01, SEC-07)

로그인 불필요. 로그인했으면 멤버십으로 판단한다. `key`는 일부 공개 블로그의 공유 링크로 들어왔을 때만 붙인다.

응답 `200 OK` (볼 수 있을 때)
```json
{
  "slug": "jeju-month",
  "name": "제주 한 달 살기",
  "description": "제주에서 한 달 지낸 기록",
  "coverImageUrl": null,
  "tags": ["여행", "제주_맛집", "jeju"],
  "visibility": "UNLISTED",
  "joinPolicy": "APPROVAL",
  "memberCount": 1,
  "ownerNickname": "제주사람",
  "createdAt": "2026-10-07T17:30:00+09:00",
  "myRole": "OWNER",
  "shareUrl": "/blog/jeju-month?key=9f86d081884c7d659a2feaa0c55ad015"
}
```
- `myRole`: 내 역할. 멤버가 아니거나 로그인하지 않았으면 `null`.
- `shareUrl`: `UNLISTED`이고 내가 멤버(블로그장 포함)일 때만 있다. 비공개·공개 블로그에는 없다.

오류 (블로그 정보는 하나도 담지 않는다)
| 상태 | code | message | 언제 |
|---|---|---|---|
| 404 | `BLOG_NOT_FOUND` | 블로그를 찾을 수 없습니다. | 없는 주소, 폐쇄·삭제된 블로그 |
| 403 | `LINK_REQUIRED` | 링크가 있어야 볼 수 있는 블로그입니다. | 일부 공개 블로그를 멤버가 아닌 사람이 `key` 없이 또는 틀린 `key`로 요청 |
| 403 | `PRIVATE_BLOG` | 비공개 블로그입니다. | 비공개 블로그를 멤버가 아닌 사람이 요청 |

## GET /blog/{slug} — 블로그 첫 화면 (D-70)

로그인 불필요. 항상 `blog.html`을 돌려주고, 화면이 위 API로 내용을 채운다. 응답 HTML에는 블로그 정보가 들어 있지 않다.
