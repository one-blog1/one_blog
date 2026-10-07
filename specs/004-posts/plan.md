# Implementation Plan: 글 작성·조회·수정·삭제, 목록

**Branch**: `main` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

## Summary

Flyway V4로 ERD의 `posts`를 추가한다(`fk_posts_categories`는 008). 본문은 마크다운 원문으로 저장하고, 읽을 때 commonmark로 HTML을 만든 뒤 jsoup 허용 목록으로 거른다(`MarkdownRenderer`). 볼 수 있는지는 `BlogAccessService`, 쓰기·지우기 권한은 요청마다 `blog_members`로 확인한다. 이미지·좋아요·카테고리·태그·조회수가 글 흐름에 끼어들 수 있게 `PostExtension` 자리를 둔다.

## Technical Context

- 새 의존성: `org.commonmark:commonmark` 0.24.0 (+ gfm-tables, gfm-strikethrough), `org.jsoup:jsoup` 1.18.1
- 나머지는 001~003과 같다.

## Research

### R1. 마크다운 → 안전한 HTML (SEC-06, D-64)

- **Decision**: commonmark-java로 HTML을 만들 때 `escapeHtml(true)`(본문 안의 HTML은 글자로), `sanitizeUrls(true)`(javascript: 등 제거)를 켜고, 결과를 jsoup `Safelist.relaxed()`(+ hr·del·s, code의 class, img는 https와 상대 주소만)로 한 번 더 거른다. 모든 링크에 `rel="nofollow noopener noreferrer"`, `target="_blank"`.
- **Rationale**: 7장 결정(서버에서 jsoup)과 맞고, 두 겹으로 막는다. 마크다운 원문을 저장하므로 허용 목록을 바꾸면 기존 글에도 바로 적용된다.
- **Alternatives**: 저장할 때 HTML로 바꿔 저장 — ERD는 마크다운 원문(`posts.content`) 저장이고, 규칙을 바꿀 때 기존 글을 다시 만들어야 한다. flexmark — 기능이 많지만 무겁다.

### R2. 부가 기능 연결 (`PostExtension`)

- **Decision**: `validate`·`afterSave`·`afterDelete`·`describe`·`describeList`를 가진 인터페이스를 두고, 각 기능(005 이미지, 006 좋아요, 008 카테고리·태그, 014 조회수, 011 알림)이 빈을 하나씩 추가한다.
- **Rationale**: 글 서비스가 기능마다 커지지 않고, 기능을 끄거나 바꿀 때 한 클래스만 본다 (constitution V).

### R3. 글쓰기 중 로그인 연장 (D-62)

- **Decision**: `js/session-keeper.js`가 입력이 있을 때 1분에 한 번 `GET /api/me`를 `X-User-Activity: 1`로 보내 30분 무활동 시계를 다시 시작한다. 25분 입력이 없으면 안내와 연장 버튼을 보여준다. 30초마다 브라우저(localStorage)에 임시 저장한다 (6.6).
- **Rationale**: 001의 활동 헤더 규칙을 그대로 쓰므로 서버 변경이 없다.

## Constitution Check

| 원칙 | 확인 | 결과 |
|---|---|---|
| I. 요구사항 ID | BRD-01·02, 6.6, D-07·33·58·62·64·92 | ✅ |
| II. 미룬 항목 | spec 후속 작업 | ✅ |
| III. 역할 DB 확인 | 쓰기·지우기·공지는 요청마다 `blog_members` | ✅ |
| III. innerHTML은 거른 본문만 | `post.js`의 `post-body`만 innerHTML | ✅ |
| III. SQL 바인딩 | JPQL만 | ✅ |
| IV. ERD·Flyway | V4 `posts`, FK 2개, 인덱스 5개 (FULLTEXT 포함). `fk_posts_categories`는 008 | ✅ |
| IV. 소프트 삭제 | `deleted_at`, `deleted_by` | ✅ |
| 권한 거부 테스트 | 비멤버·비회원·관리자 쓰기, 남의 글 고치기, 권한 없는 지우기 | ✅ |

## API

| 메서드·주소 | 권한 | 비고 |
|---|---|---|
| `GET /api/blogs/{slug}/posts?page&size&category&key` | 블로그를 볼 수 있음 | `{notices[], items[], page, size, totalItems, totalPages}` |
| `POST /api/blogs/{slug}/posts?key` | 활성 멤버 | `{title, content, notice?, categoryId?, tags?, imageFileIds?}` → 201 `{id, url}` |
| `GET /api/posts/{id}?key` | 블로그를 볼 수 있음 | `contentHtml`(거른 HTML), `content`(작성자에게만), `canEdit`, `canDelete`, `canComment` |
| `PUT /api/posts/{id}` | 작성자 본인 | |
| `DELETE /api/posts/{id}` | 작성자, 글 관리 권한 | 204 |
| `GET /blog/{slug}/posts/{id}` | 누구나 | `post.html` |
| `GET /blog/{slug}/write`, `/blog/{slug}/posts/{id}/edit` | 누구나(데이터는 API가 지킴) | `post-edit.html` |

오류: `NOT_A_MEMBER`(403), `FORBIDDEN`(403), `ADMIN_NOT_ALLOWED`(403), `USE_ADMIN_API`(403), `POST_NOT_FOUND`(404), `VALIDATION_FAILED`(400).

## Project Structure

```text
src/main/java/com/oneblog/post/        Post, PostType, DeletedBy, PostRepository, PostPolicy, PostService,
                                       PostController, PostExtension, PostView, PostListContext, dto/
src/main/java/com/oneblog/common/text/MarkdownRenderer.java
src/main/java/com/oneblog/common/web/  PageParams, Times
src/main/java/com/oneblog/member/UserDisplayService.java
src/main/resources/db/migration/V4__create_posts.sql
src/main/resources/static/             post.html, post-edit.html, js/post.js, js/post-edit.js, js/post-item.js,
                                       js/pager.js, js/blog-posts.js, js/session-keeper.js
src/test/java/com/oneblog/post/        PostTestSupport, PostIntegrationTest
```
