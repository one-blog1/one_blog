# Research: 002 블로그 생성·목록·내 블로그

기술 스택은 001에서 정한 것(Spring Boot 4.1, Java 21, Spring Security, JPA, Flyway, MySQL 8.4, 정적 HTML + JS)을 그대로 쓴다. 여기서는 이 기능에서 새로 정해야 하는 구현 세부만 정한다.

## R1. 블로그 첫 화면 주소 `/blog/{주소}` 처리 (D-70, D-66)

- **Decision**: `src/main/resources/static/blog.html` 하나를 두고, `GET /blog/{slug}` 요청을 작은 컨트롤러(`BlogPageController`)가 `forward:/blog.html`로 넘긴다. 화면의 `blog.js`가 주소창에서 `slug`와 `key`(공유 링크 값)를 읽어 `GET /api/blogs/{slug}?key=...`를 부른다. 블로그가 있는지, 볼 수 있는지는 API가 판단한다.
- **Rationale**: 정적 HTML 방식(D-66)을 유지하면서 경로 방식 주소(D-70)를 쓸 수 있다. HTML 자체에는 블로그 내용이 없어 누구에게 보여도 새는 정보가 없다. 글 상세의 og 태그(BRD-07)는 004/014에서 같은 방식(서버가 HTML에 og 태그만 채움)으로 확장한다.
- **Alternatives considered**: 블로그마다 Thymeleaf로 HTML을 만들기 — D-66에서 정적 HTML로 정해 제외. `/blog.html?slug=` 쿼리 주소 — D-70의 `/blog/{주소}`와 다르다.

## R2. 블로그 주소 규칙과 예약어 (6.5)

- **Decision**:
  - 정리: 앞뒤 공백 제거 → 소문자로 바꿈.
  - 형식: `^[a-z0-9]+(-[a-z0-9]+)*$` 이고 길이 3~30자. `-`로 시작·끝나거나 `--`가 들어가면 거부한다.
  - 예약어: `app.blog.reserved-slugs` 설정 목록(기본값 `main, admin, api, login, signup, search, blog, blogs, my, me, new, css, js, images, files, error, static, favicon, notice, settings, help`). 비교는 정리한 값으로 한다.
  - 미리 확인: `GET /api/blog-slugs/availability?slug=`가 `INVALID_FORMAT`, `RESERVED`, `TAKEN` 중 하나 또는 사용 가능을 돌려준다(닉네임 확인과 같은 형태).
  - 최종 판단: 만들기 요청에서 다시 검사하고, 동시에 같은 주소로 만들면 `uk_blogs_slug` 위반을 잡아 `SLUG_TAKEN`(409)으로 돌려준다.
- **Rationale**: 닉네임 확인(001 `NicknameService`)과 같은 흐름이라 화면과 서버 코드를 재사용한다. 유일 제약이 최종 방어선이라 동시 요청에도 안전하다. 예약어를 설정에 두면 사이트 경로가 늘 때 코드 수정 없이 더한다.
- **Alternatives considered**: 예약어를 코드 상수로 — 경로가 늘 때마다 코드를 고쳐야 한다. 주소를 미리 잠그는 예약 테이블 — 1차에 필요 없는 복잡도(constitution V).

## R3. 생성 개수 제한과 동시 요청 (BLG-10, D-51, D-52, D-68)

- **Decision**:
  - 설정: `app.blog.limit.public=${BLOG_LIMIT_PUBLIC:3}`, `app.blog.limit.private=${BLOG_LIMIT_PRIVATE:5}`. 시작할 때 공개 1~5, 비공개 1 이상인지 확인한다(7장 개수 제한값).
  - 셀 대상: 그 회원이 `blog_members.role = 'OWNER'`, `status = 'ACTIVE'`인 블로그 중 `blogs.status <> 'CLOSED'`이고 `deleted_at IS NULL`인 것. 공개 칸은 `PUBLIC` + `UNLISTED`, 비공개 칸은 `PRIVATE`.
  - 잠금: 만들기 트랜잭션 처음에 `SELECT id FROM users WHERE id = ? FOR UPDATE`(JPA `@Lock(PESSIMISTIC_WRITE)`)로 그 회원 행을 잠근 뒤 개수를 세고, 블로그·멤버십·태그를 저장한다. 같은 회원의 두 번째 요청은 첫 요청이 끝날 때까지 기다렸다가 늘어난 개수를 본다.
  - 초과하면 `BLOG_LIMIT_EXCEEDED`(409)와 공개/비공개 구분, 제한값을 돌려준다.
- **Rationale**: 7장이 "회원 행 `SELECT ... FOR UPDATE`"를 예로 들었고, 잠그는 범위가 그 회원 한 명이라 다른 회원의 만들기를 막지 않는다. 서버가 여러 대여도 DB 잠금이라 그대로 동작한다(SCL-01).
- **Alternatives considered**: 개수 컬럼을 users에 두고 조건부 UPDATE — ERD에 없는 컬럼이 생긴다(constitution IV). 애플리케이션 `synchronized` — 서버가 여러 대면 깨진다(SCL-01).

## R4. 파일 업로드와 저장 (6.3, SEC-08, SCL-02)

- **Decision**:
  - 흐름: 화면이 이미지를 먼저 `POST /api/files/blog-cover`(multipart)로 올리고 `fileId`, `url`을 받는다. 블로그 만들기 요청에 `coverFileId`를 넣으면 서버가 "내가 올린, 삭제되지 않은 BLOG_COVER 파일"인지 확인하고 `blogs.cover_image_url`에 `/files/{stored_name}`을 저장한다.
  - 검사(셋 다 맞아야 통과): 확장자 `jpg, jpeg, png, gif, webp`(소문자로 비교), 요청의 MIME 타입 `image/jpeg, image/png, image/gif, image/webp`, 파일 앞부분 바이트(JPEG `FF D8 FF`, PNG `89 50 4E 47 0D 0A 1A 0A`, GIF `GIF87a`/`GIF89a`, WebP `RIFF....WEBP`). 세 판단이 가리키는 형식이 서로 같아야 한다. 크기는 3MB 이하.
  - 위치 정보 제거: 다시 그리지(재인코딩) 않고 메타데이터 부분만 뺀다. JPEG는 APP1(Exif·XMP) 구간, PNG는 `eXIf`·`tEXt`·`iTXt`·`zTXt` 덩어리, WebP는 `EXIF`·`XMP ` 덩어리(VP8X 표시 비트도 끔)를 지운다. GIF는 위치 정보가 없어 그대로 둔다. `ImageMetadataStripper` 한 클래스에 형식별로 둔다.
  - 저장: `FileStorage` 인터페이스(`store`, `open`) 뒤에 로컬 디스크 구현 `LocalFileStorage`를 둔다. 경로는 `app.file.storage-dir=${FILE_STORAGE_DIR:./uploads}`, 이름은 `UUID + 확장자`(`stored_name`), 원래 이름은 `files.original_name`에만 둔다. `uploads/`는 `.gitignore`에 넣는다.
  - 내려주기: `GET /files/{storedName}`이 DB에서 파일 행을 찾아(없거나 삭제됐으면 404) 저장된 MIME 타입과 `X-Content-Type-Options: nosniff`, `Content-Disposition: inline`, `Cache-Control: public, max-age=86400`으로 보낸다. 이름 형식(`^[0-9a-f-]{36}\.(jpg|jpeg|png|gif|webp)$`)이 아니면 디스크를 보지 않고 404다.
  - 업로드 크기: `spring.servlet.multipart.max-file-size=3MB`, `max-request-size=10MB`(005의 글 이미지 묶음 업로드 대비). 초과는 `FILE_TOO_LARGE`(413).
- **Rationale**: D-75(서버 디스크 1차, 이중화 때 S3)와 SCL-02(`FileStorage` 인터페이스)를 그대로 따른다. 재인코딩은 화질이 떨어지고 Java 기본 ImageIO가 WebP를 못 읽으며 GIF 움직임이 사라져 제외했다. 메타데이터 구간만 지우면 의존성이 없고 원본 화질이 그대로다.
- **Alternatives considered**: Apache Commons Imaging — JPEG Exif 지우기는 되지만 WebP·PNG 텍스트 덩어리를 다 다루지 못해 직접 구현과 섞이게 된다. 블로그를 만들 때 이미지도 같은 요청(multipart)으로 받기 — 미리보기를 위해 어차피 먼저 올려야 하고, 005·009가 같은 업로드 API를 재사용할 수 있게 나눴다.
- **알려진 한계**: 대표 이미지는 무작위 이름을 아는 사람이면 누구나 받을 수 있다. 블로그 API가 볼 수 없는 사람에게 주소를 주지 않으므로(FR-033) 1차에는 이것으로 충분하다. 글 이미지(005)는 글 권한을 따라야 하므로 그때 내려주기 규칙을 다시 정한다.

## R5. 태그 정리와 저장 (6.4, D-88)

- **Decision**:
  - 정리 규칙은 `TagPolicy`(새 `tag` 패키지)에 둔다: 앞의 `#` 여러 개와 앞뒤 공백 제거 → 가운데 공백 묶음을 `_` 하나로 → 영문 소문자 → `^[가-힣a-z0-9_]{1,20}$` 검사 → 같은 값 합치기(입력 순서 유지) → 10개 이하 확인.
  - 저장: 태그 이름마다 `INSERT IGNORE INTO tags (name) VALUES (?)` 후 `SELECT id FROM tags WHERE name IN (...)`로 ID를 읽어 `blog_tags`에 넣는다. 두 사람이 같은 새 태그를 동시에 만들어도 `uk_tags_name` 덕분에 하나만 생긴다.
  - 틀린 태그가 있으면 `fieldErrors`의 `field`를 `tags[2]`처럼 몇 번째인지로 알려준다.
- **Rationale**: 글 태그(008)가 같은 `tags` 테이블과 규칙을 쓰므로 블로그와 분리된 패키지에 둔다. `INSERT IGNORE`는 파라미터 바인딩이 그대로 되는 네이티브 쿼리라 constitution III을 지킨다.
- **Alternatives considered**: 먼저 찾고 없으면 저장 — 동시 요청에서 유일 제약 위반이 나 재시도 코드가 필요하다. `ON DUPLICATE KEY UPDATE` — 같은 효과지만 `INSERT IGNORE`가 짧다.

## R6. 일부 공개 공유 링크 (BLG-01, D-49, D-50)

- **Decision**: 일부 공개 블로그를 만들 때 `SecureRandom` 16바이트(128비트)를 소문자 16진수 32자로 만들어 `blogs.share_token`에 넣는다. 공유 링크는 `/blog/{slug}?key={share_token}`이다. 비교는 `MessageDigest.isEqual`로 길이·내용을 함께 본다. 공유 링크는 블로그장과 멤버에게만 응답에 넣는다(`shareUrl`). `blog.html`에는 `<meta name="referrer" content="no-referrer">`를 넣어 다른 사이트로 나가는 링크에 `key`가 실려 가지 않게 한다.
- **Rationale**: ERD 컬럼(`CHAR(32)`)과 맞고, 128비트는 추측이 불가능하다. 공유 링크는 블로그장이 다시 복사해야 하므로 해시가 아닌 원래 값으로 저장한다(ERD 설계 그대로). 링크를 새로 만드는 기능은 후속 작업이다.
- **Alternatives considered**: 공유 링크를 별도 테이블로 여러 개 — ERD에 없다. 링크 값을 해시로 저장 — 블로그장이 다시 볼 수 없다.

## R7. 블로그를 볼 수 있는지 판단 (SEC-07, FR-032, FR-033)

- **Decision**: `BlogAccessService.check(slug, key, viewer)`가 한 곳에서 판단한다.
  1. `slug`(정리한 값)로 `deleted_at IS NULL`이고 `status <> 'CLOSED'`인 블로그를 찾는다. 없으면 `BLOG_NOT_FOUND`(404).
  2. 로그인했다면 `blog_members`에서 그 회원의 `ACTIVE` 멤버십을 찾는다. 있으면 볼 수 있고, 역할(OWNER 등)을 함께 돌려준다.
  3. 멤버가 아니면: `PUBLIC` → 볼 수 있음. `UNLISTED` → `key`가 맞으면 볼 수 있음, 아니면 `LINK_REQUIRED`(403). `PRIVATE` → `PRIVATE_BLOG`(403).
  4. 403 응답에는 오류 코드와 안내 문구만 넣고 블로그 정보는 넣지 않는다. 틀린 `key`와 `key` 없음은 같은 응답이다.
- **Rationale**: 역할을 화면이나 토큰이 아니라 매번 DB의 멤버십으로 확인한다(constitution III). 이후 기능(글 목록 004, 참여 003)이 같은 서비스를 불러 같은 규칙을 쓴다. 정지·블랙리스트(013)와 관리자 숨김(007)은 이 서비스에 조건을 더한다.
- **Alternatives considered**: 블로그장·멤버는 `@PreAuthorize`로 확인 — SEC-11이 메서드 보안을 말하지만, 이 판단은 비회원과 공유 링크까지 섞여 있어 서비스 한 곳이 더 명확하다. 블로그 관리처럼 역할만 보는 기능(후속)은 `@PreAuthorize("@blogAuth.isOwner(#blogId)")` 형태로 같은 멤버십 조회를 쓴다.

## R8. 목록 조회와 페이징 (BLG-02, BLG-06, D-06, D-76, D-89)

- **Decision**:
  - 메인 목록 `GET /api/blogs?sort=latest|popular&page=1&size=10`. 조건 `visibility = 'PUBLIC' AND status <> 'CLOSED' AND is_hidden = 0 AND deleted_at IS NULL`. 정렬 최신순 `created_at DESC, id DESC`, 인기순 `member_count DESC, created_at DESC, id DESC`.
  - `page`는 1부터. `size`가 10·20·30이 아니면 10, `page`가 1보다 작거나 마지막 페이지보다 크면 1페이지로 바꿔 응답에 실제 값을 넣는다(스펙 Edge Cases).
  - 블로그장 닉네임은 그 페이지의 블로그 ID로 `blog_members(role='OWNER')` + `users`를 한 번에, 태그는 `blog_tags` + `tags`를 한 번에 조회해 붙인다(블로그마다 따로 조회하지 않음).
  - 내 블로그 `GET /api/me/blogs`는 내 `ACTIVE` 멤버십이 있는 폐쇄되지 않은 블로그를 역할로 나눠 `owned`(OWNER)와 `joined`(SUB_OWNER, MEMBER)로 돌려준다. 페이징하지 않는다(만들 수 있는 블로그가 최대 8개이고 참여 블로그도 많지 않음).
  - ERD의 인덱스 `idx_blogs_visibility_status_created_at`, `idx_blogs_visibility_status_member_count`, `idx_blog_members_user_id_status`를 그대로 쓴다.
- **Rationale**: 번호 페이지(D-76)에는 전체 개수가 필요해 Spring Data의 `Page`를 쓴다. 목록 한 번에 쿼리가 네 번(목록, 개수, 블로그장, 태그)으로 고정돼 1만 개에서도 2초 목표(SC-002)를 지킨다.
- **Alternatives considered**: 커서 방식 — 번호 페이지가 확정돼 제외. 태그를 블로그마다 지연 로딩 — 한 페이지에 최대 31번 쿼리가 나간다.

## R9. 화면 구성 (D-66, SEC-06)

- **Decision**:
  - `index.html`: 기존 메인에 블로그 목록(정렬 버튼, 한 페이지 개수 선택, 번호 페이지)을 넣는다. `js/blog-list.js`.
  - `blog-new.html`: 블로그 만들기. 주소 확인 버튼, 대표 이미지 올리기와 미리보기, 태그 입력(쉼표·Enter로 나눔), 공개 범위·참여 방식 선택, 남은 개수 표시. `js/blog-new.js`.
  - `my-blogs.html`: 내가 만든 블로그 / 참여한 블로그. `js/my-blogs.js`.
  - `blog.html`: `/blog/{slug}`가 넘겨받는 첫 화면. 안내 화면(링크 필요, 비공개, 없음)과 공유 링크 복사 버튼. `js/blog.js`.
  - 상단 메뉴(`header.js`)에 로그인한 회원에게 "내 블로그", "블로그 만들기"를 더한다.
  - 사용자 입력(이름, 소개, 태그, 닉네임)은 모두 `textContent`로 넣는다. 블로그로 가는 링크는 서버가 준 `slug`로 `/blog/` + `encodeURIComponent(slug)`를 만든다.
  - 공통 목록 그리기(블로그 카드 하나 만들기)는 `js/blog-card.js`에 두고 메인 목록과 내 블로그가 함께 쓴다.
- **Rationale**: 001의 화면 방식 그대로이고, 정적 HTML이 길어지지 않게 카드 그리기를 한 파일로 모은다(D-66 유지 결정 때 "공통 함수를 빼 두기"로 합의).
- **Alternatives considered**: 메인 목록을 별도 페이지로 — 메인블로그의 첫 화면이 블로그 목록이라 index에 둔다.

## R10. 주소별 접근 권한 (SEC-11)

- **Decision**: `SecurityConfig`의 주소 규칙을 아래 순서로 더한다(위에 있는 규칙이 먼저 맞음).
  - 누구나 GET: `/blog/**`, `/blog-new.html`, `/my-blogs.html`, `/blog.html`, `/files/**`, `/api/blogs`, `/api/blogs/*`
  - 로그인 필요: `/api/blog-slugs/**`, `/api/me/**`, `POST /api/blogs`, `POST /api/files/**` (기존 `/api/**` authenticated 규칙이 이미 덮음)
  - 화면 HTML은 누구나 받되, 로그인이 필요한 화면(`blog-new.html`, `my-blogs.html`)은 JS가 API의 401을 받으면 로그인 화면으로 보낸다.
  - 메인 관리자(`role = ADMIN`)의 블로그 만들기는 서비스에서 `ADMIN_NOT_ALLOWED`(403)로 막는다 (D-90).
- **Rationale**: 정적 HTML에는 데이터가 없어 공개해도 되고, 데이터는 API가 지킨다. `/api/blogs/*` GET을 공개하므로 도우미 API(주소 확인, 남은 개수)는 `/api/blogs/` 아래에 두지 않아 블로그 주소와 겹치지 않게 했다(예: 주소가 `quota`인 블로그).
- **Alternatives considered**: 도우미 API를 `/api/blogs/quota`에 두고 예약어로 막기 — 예약어가 API 경로에 묶여 실수하기 쉽다.
