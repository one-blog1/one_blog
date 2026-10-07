# Implementation Plan: 블로그 생성·목록·내 블로그

**Branch**: `main` (constitution: main에 바로 올림) | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-blog-create-list/spec.md`

## Summary

회원이 이름·주소·소개·대표 이미지·태그·공개 범위·참여 방식을 정해 블로그를 만들고 블로그장이 되는 기능과, 메인 화면의 공개 블로그 목록(최신순·인기순, 번호 페이지), 내 블로그 목록, 공개 범위에 따른 블로그 첫 화면(`/blog/{주소}`) 접근 판단을 만든다. Flyway V2로 ERD의 `blogs`, `blog_members`, `tags`, `blog_tags`, `files`를 추가한다. 생성 개수 제한은 회원 행 `SELECT ... FOR UPDATE`로 동시 요청을 막고([research.md](research.md) R3), 대표 이미지는 `FileStorage` 인터페이스 뒤 서버 디스크에 UUID 이름으로 위치 정보를 지워 저장한다(R4). 볼 수 있는지는 `BlogAccessService` 한 곳이 저장된 멤버십으로 판단한다(R7). 화면은 001과 같은 정적 HTML + JS다(D-66).

## Technical Context

**Language/Version**: Java 21 (Temurin)

**Primary Dependencies**: 001과 같음 — Spring Boot 4.1.x(webmvc, security, oauth2-jose, data-jpa, validation, mail, flyway), MySQL Connector/J. 이 기능에서 새 의존성은 없다 (이미지 위치 정보 제거는 직접 구현, R4).

**Storage**: MySQL 8.4 (utf8mb4), Flyway V2 마이그레이션. 업로드 파일은 서버 디스크(`FILE_STORAGE_DIR`, 기본 `./uploads`) (D-75)

**Testing**: JUnit 5, Spring Boot Test + MockMvc, `spring-security-test`, 로컬 MySQL `one_blog_test` 통합 테스트 (001 research R8)

**Target Platform**: 개발 macOS(Apple Silicon), 운영 Linux 서버 1대 (4.3)

**Project Type**: 웹 서비스 — Spring Boot 하나가 REST API와 정적 HTML·JS 화면을 함께 제공 (D-66)

**Performance Goals**: 메인 목록·내 블로그 2초 안 (4.2, SC-002). 목록 한 번에 쿼리 4번(목록·개수·블로그장·태그)으로 고정 (R8)

**Constraints**: 서버 메모리에 상태 없음 (SCL-01). 비공개·일부 공개 블로그 정보가 볼 수 없는 사람의 응답에 하나도 없음 (SC-004). 개수 제한은 동시 요청에도 지킴 (SC-003)

**Scale/Scope**: 화면 4개(메인 목록, 블로그 만들기, 내 블로그, 블로그 첫 화면), API 7개 + 파일 받기 1개, 테이블 5개

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 확인 | 결과 |
|---|---|---|
| I. 요구사항 문서가 기준 | 스펙에 BLG-01·02·06·10, SEC-07·08, 6.3~6.5, D-49~52·68·70·76·88·89·90 인용. 요구사항 문서 변경 없음 | ✅ |
| I. 보류 항목 구현 금지 | 6.4 태그 금칙어(보류)는 구현하지 않고 후속 작업에 남김 | ✅ |
| II. 작은 단위로 끝까지 | 화면 → API → DB까지 블로그 만들기·목록·첫 화면 한 흐름. 1단계 순서의 두 번째 기능(BLG-01~06 중 생성·목록·내 블로그) | ✅ |
| II. 미룬 항목 기록 | spec.md "후속 작업"에 참여(003), 글 목록(004), 구독(009), 검색(010), 정보 수정·공유 링크 재생성 등 기록 | ✅ |
| III. bcrypt | 이 기능에 비밀번호 없음 | ✅ (해당 없음) |
| III. 역할을 매 요청 DB 확인 | `BlogAccessService`가 요청마다 `blog_members`에서 역할 확인 (R7). 개수 세기도 DB 멤버십 기준 | ✅ |
| III. SQL 파라미터 바인딩 | JPA·Spring Data와 바인딩 파라미터를 쓰는 네이티브 쿼리(`INSERT IGNORE`)만 사용 (R5) | ✅ |
| III. textContent | 블로그 이름·소개·태그·닉네임 모두 `textContent`. `innerHTML` 없음 (R9) | ✅ |
| III. 서버 마스킹 | 이 기능은 이메일·전화번호를 내보내지 않음 (블로그장 닉네임만) | ✅ (해당 없음) |
| III. 비밀값 저장소 밖 | 새 비밀값 없음. 업로드 폴더 `uploads/`는 `.gitignore`에 이미 있음 | ✅ |
| IV. Flyway만, ddl-auto 금지 | `ddl-auto=validate` 유지, 스키마는 `V2__create_blog_tables.sql` | ✅ |
| IV. created_at·updated_at·deleted_at | `blogs`에 셋 다 있음. `blog_members`는 ERD대로 joined_at·updated_at·left_at, `files`는 created_at·deleted_at | ✅ |
| IV. utf8mb4 | V2의 모든 테이블 옵션 utf8mb4 | ✅ |
| IV. 블로그 멤버십 테이블 | 블로그장은 `blog_members.role = OWNER`로 저장. users에 역할을 두지 않음 | ✅ |
| IV. 서버 메모리 상태 없음 | 개수 잠금은 DB 행 잠금. 파일은 `FileStorage` 뒤 (SCL-02) | ✅ |
| IV. Crowfoot ERD 기준 | 5개 테이블을 ERD DDL(버전 6)에서 그대로 가져옴. ERD 변경 없음 | ✅ |
| IV. 쓸 테이블만 생성 | 5개만 생성. `files.post_id`의 외래 키는 `posts`가 생기는 005에서 추가 (로드맵 "002의 대표 이미지") | ✅ |
| IV. ShedLock | 이 기능에 `@Scheduled` 없음 | ✅ (해당 없음) |
| V. 단순함 | 새 의존성 없음. 공유 링크는 ERD 컬럼 하나, 개수 잠금은 회원 행 잠금 | ✅ |
| 개발 흐름: 권한 거부 테스트 | 비회원의 만들기·업로드·내 블로그 401, 관리자 403, 비멤버의 비공개·일부 공개 접근 403, 남의 이미지 사용 400 (quickstart 17~23) | ✅ |
| 개발 흐름: 릴리즈 태그 | 완료 시 `v0.2.0` | ✅ |

**Post-design 재확인 (Phase 1 후)**: data-model.md는 ERD 컬럼을 그대로 쓰고 `fk_files_posts` 하나만 005로 미뤘다(쓰지 않을 `posts` 테이블을 미리 만들지 않기 위함, constitution IV). contracts는 001의 오류 형식·CSRF 규칙을 그대로 따른다. 위반 없음.

## Project Structure

### Documentation (this feature)

```text
specs/002-blog-create-list/
├── spec.md
├── plan.md              # 이 파일
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   └── blog-api.md      # Phase 1
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks 에서 생성
```

### Source Code (repository root)

```text
src/main/java/com/oneblog/
├── blog/                         # 블로그 — 엔티티(Blog, BlogMember), 리포지토리, 정책, 서비스, 컨트롤러
│   ├── Blog.java, BlogVisibility.java, BlogJoinPolicy.java, BlogStatus.java
│   ├── BlogMember.java, BlogRole.java, BlogMemberStatus.java
│   ├── BlogRepository.java, BlogMemberRepository.java
│   ├── BlogPolicy.java           # 이름·소개·주소 정리와 규칙, 예약어 (R2)
│   ├── BlogProperties.java       # app.blog.* (제한값, 예약어)
│   ├── BlogCreateService.java    # 만들기 + 개수 제한 (R3)
│   ├── BlogQueryService.java     # 메인 목록, 내 블로그, 남은 개수 (R8)
│   ├── BlogAccessService.java    # 볼 수 있는지 판단 (R7)
│   ├── BlogApiController.java    # /api/blogs, /api/me/blogs, /api/me/blog-quota, /api/blog-slugs
│   ├── BlogPageController.java   # GET /blog/{slug} → blog.html
│   └── dto/
├── tag/                          # 태그 — 정리 규칙과 저장 (R5). 글 태그(008)도 사용
│   ├── TagPolicy.java, TagService.java
├── file/                         # 업로드 — FileStorage(SCL-02), 검사, 위치 정보 제거 (R4)
│   ├── StoredFile.java, FilePurpose.java, StoredFileRepository.java
│   ├── FileStorage.java, LocalFileStorage.java, FileProperties.java
│   ├── ImageTypeDetector.java, ImageMetadataStripper.java
│   ├── FileUploadService.java, FileController.java
├── member/UserRepository.java    # 회원 행 잠금 조회 추가 (R3)
└── common/
    ├── config/SecurityConfig.java       # 주소 규칙 추가 (R10)
    └── web/GlobalExceptionHandler.java  # 업로드 크기 초과 413

src/main/resources/
├── application.yml                       # app.blog.*, app.file.*, multipart 크기
├── db/migration/V2__create_blog_tables.sql
└── static/
    ├── index.html (목록 추가), blog-new.html, my-blogs.html, blog.html
    ├── css/app.css (블로그 카드·목록·페이지 번호)
    └── js/ (api.js에 파일 업로드 추가, header.js 메뉴 추가, blog-card.js, blog-list.js, blog-new.js, my-blogs.js, blog.js)

src/test/java/com/oneblog/
├── IntegrationTestSupport.java   # 블로그 테이블 정리, 로그인 쿠키 도우미
├── blog/  (BlogCreateIntegrationTest, BlogLimitIntegrationTest, BlogListIntegrationTest, BlogAccessIntegrationTest, BlogPagesIntegrationTest, BlogPolicyTest)
├── tag/   (TagPolicyTest)
└── file/  (FileUploadIntegrationTest, ImageTypeDetectorTest, ImageMetadataStripperTest)
```

**Structure Decision**: 001의 기능 단위 패키지 구조를 그대로 잇는다. 태그와 파일은 블로그 말고도 글(004~008)·프로필(009)이 함께 쓰므로 `blog` 안이 아니라 같은 수준의 `tag`, `file` 패키지로 둔다.

## Complexity Tracking

constitution 위반이 없어 비워 둔다.
