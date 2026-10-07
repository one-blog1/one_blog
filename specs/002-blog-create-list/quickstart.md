# Quickstart: 002 블로그 생성·목록·내 블로그 검증

## 준비

001 [quickstart](../001-signup-login/quickstart.md)의 준비(JDK 21, 테스트 DB, `.env`)를 마친 상태에서 시작한다.

- 이 기능에서 `.env`에 더할 수 있는 값 (모두 기본값이 있어 비워 둬도 됨)
  ```bash
  FILE_STORAGE_DIR=./uploads   # 대표 이미지 저장 폴더. 저장소에 올리지 않음
  BLOG_LIMIT_PUBLIC=3          # 공개(일부 공개 포함) 블로그 제한, 1~5
  BLOG_LIMIT_PRIVATE=5         # 비공개 블로그 제한
  ```
- 첫 실행 때 Flyway가 `V2__create_blog_tables.sql`을 적용한다. 확인: `SELECT version, success FROM flyway_schema_history;` 에 2가 있어야 한다.
- 테스트용 이미지: 작은 jpg·png·gif·webp 하나씩, 3MB를 넘는 jpg 하나, 확장자만 `.jpg`로 바꾼 텍스트 파일 하나, 위치 정보가 들어 있는 휴대폰 사진(jpg) 하나.

## 실행

```bash
./gradlew test       # 통합 테스트
./gradlew bootRun    # http://localhost:8080
```

회원 A, B 두 계정을 001 방식으로 가입해 둔다.

## 검증 시나리오

API 상세는 [contracts/blog-api.md](contracts/blog-api.md), 테이블은 [data-model.md](data-model.md)를 본다.

| # | 시나리오 | 기대 결과 | 스펙 |
|---|---|---|---|
| 1 | A로 로그인 → 블로그 만들기에서 이름·주소(`a-public`)·공개·자유로 만들기 | `/blog/a-public`으로 이동, A가 블로그장, `blogs` 1행·`blog_members` 1행(OWNER), `member_count = 1` | US1-1 / FR-001, 002 |
| 2 | 주소에 `Admin`, `ab`, `-abc`, `a--b`, `a-public` 입력 후 확인 | 각각 RESERVED, INVALID_FORMAT, INVALID_FORMAT, INVALID_FORMAT, TAKEN | US1-2, 3 / FR-007~009 |
| 3 | 대표 이미지로 jpg·png·gif·webp 각각 올리기 | 미리보기가 보이고 `files`에 BLOG_COVER 행, 디스크에 UUID 이름으로 저장 | US1-4 / FR-011, 013 |
| 4 | 3MB 초과 jpg, svg, 확장자만 바꾼 텍스트 파일 올리기 | 각각 413 FILE_TOO_LARGE, 400 UNSUPPORTED_FILE_TYPE, 400 UNSUPPORTED_FILE_TYPE. 디스크와 `files`에 남지 않음 | US1-5 / FR-011, 012, SC-006 |
| 5 | 위치 정보가 있는 사진을 올린 뒤 `/files/{이름}`으로 받아 Exif 확인(`exiftool` 또는 사진 정보 보기) | GPS 정보가 없음 | FR-013 |
| 6 | 태그 `#맛집`, ` 서울 여행 `, `Java`, `java` 로 만들기 | 저장된 태그가 `맛집`, `서울_여행`, `java` 3개 | US1-6 / FR-016 |
| 7 | 태그 11개, `맛집!`, 21자 태그로 만들기 | 400 VALIDATION_FAILED, 어느 태그인지 표시, 블로그 안 생김 | US1-7 / FR-015, 017 |
| 8 | A로 일부 공개 블로그 `a-link` 만들기 | 응답에 `shareUrl`(`?key=` + 32자), 첫 화면에 공유 링크 복사 버튼 | US1-8 / FR-019, 034 |
| 9 | A가 공개 2개 + 일부 공개 1개를 가진 상태에서 공개 하나 더 만들기 | 409 BLOG_LIMIT_EXCEEDED(PUBLIC, 3) | US2-1 / FR-021 |
| 10 | 9번 상태에서 비공개 5개 만들고 6번째 만들기 | 5개까지 되고 6번째는 409(PRIVATE, 5) | US2-2, 3 |
| 11 | 공개 2개인 회원으로 만들기 요청 10개를 동시에 보내기(테스트 코드) | 1개만 201, 나머지 409. 공개 블로그가 3개를 넘지 않음 | US2-4 / FR-023, SC-003 |
| 12 | `BLOG_LIMIT_PUBLIC=5`로 재시작 후 9번 상태에서 만들기 | 201 | US2-5 / FR-022 |
| 13 | 로그아웃 상태로 메인 화면 | 공개 블로그만 최신순 10개, 일부 공개·비공개는 없음, 전체 페이지 수 표시 | US3-1, 2 / FR-025, 027, SC-005 |
| 14 | 메인에서 인기순, 20개, 2페이지 선택 / `?page=999&size=7` 요청 | 멤버 수 순서, 20개씩 / 1페이지 10개로 응답 | US3-3, 4 / FR-026, 027 |
| 15 | A로 내 블로그 화면 | "내가 만든 블로그"에 공개·일부 공개·비공개 모두 공개 범위와 함께 표시, "참여한 블로그"는 비어 있음 | US4-1, 2 / FR-029, SC-007 |
| 16 | 로그아웃 상태로 `/my-blogs.html`, `/blog-new.html` 열기 | 로그인 화면으로 이동 | US4-4 |
| 17 | 로그아웃 상태와 B로 `/blog/a-link` 열기 | "링크가 있어야 볼 수 있는 블로그입니다", API 응답에 블로그 이름·소개 없음 | US5-2 / FR-033, SC-004 |
| 18 | 로그아웃 상태와 B로 8번의 공유 링크 열기 / key 한 글자 바꿔 열기 | 블로그 첫 화면이 보이고 공유 버튼은 없음 / 17번과 같은 안내 | US5-3 / FR-020 |
| 19 | 로그아웃 상태와 B로 A의 비공개 블로그 열기, A로 같은 블로그 열기 | B: "비공개 블로그입니다"(403 PRIVATE_BLOG, 정보 없음) / A: 첫 화면 | US5-4, 5 / FR-032, 033 |
| 20 | `/blog/no-such-blog` 열기 | "블로그를 찾을 수 없습니다" (404) | US5-6 |
| 21 | 로그인 없이 `POST /api/blogs`, `POST /api/files/blog-cover`, `GET /api/me/blogs` 요청 | 모두 401 | FR-003 / 권한 거부 테스트 |
| 22 | 관리자 계정(DB로 만든 role=ADMIN)으로 블로그 만들기·이미지 올리기 | 403 ADMIN_NOT_ALLOWED | US1-9 / FR-003, D-90 |
| 23 | B가 A의 대표 이미지 `fileId`로 블로그 만들기 | 400 INVALID_COVER_FILE | FR-011 (본인 파일만) |
| 24 | 이름 `<script>alert(1)</script>`, 태그 `script`로 공개 블로그 만들기 → 메인·첫 화면 확인 | 이름이 글자 그대로 보이고 실행되지 않음 | FR-036 |
| 25 | `X-XSRF-TOKEN` 없이 `POST /api/blogs` | 403 | FR-035 |
