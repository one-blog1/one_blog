# Implementation Plan: 이미지 첨부·에디터

**Branch**: `main` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

## Summary

Flyway V5로 002에서 미룬 `fk_files_posts`를 ERD대로 추가한다. 글 이미지는 `POST /api/files/post-image`로 먼저 올리고(002의 `FileUploadService` 검사 그대로, 용도 `POST`), 글을 저장할 때 `PostImageExtension`이 본문 마크다운의 `/files/{저장 이름}`을 찾아 작성자 본인 이미지만 그 글에 연결한다. 에디터는 Toast UI Editor 3.2.2를 CDN에서 불러오고 `addImageBlobHook`으로 업로드한다.

## Research

### R1. 이미지와 글 연결 방법

- **Decision**: 화면이 보낸 파일 번호 목록 대신 서버가 본문에서 주소를 읽어 연결한다. 본문 순서가 `sort_order`, 첫 장이 썸네일.
- **Rationale**: 화면 상태와 본문이 어긋날 일이 없고, 본문에서 지운 이미지를 자연스럽게 찾을 수 있다.
- **Alternatives**: 화면이 `imageFileIds`를 보냄 — 본문과 목록이 어긋날 수 있다. 요청 필드는 남겨 두되 쓰지 않는다.

### R2. 에디터 불러오기 (D-74)

- **Decision**: `uicdn.toast.com/editor/3.2.2`의 css·js·한국어 파일을 버전 고정으로 불러온다. 불러오지 못하면 글상자로 쓴다.
- **Rationale**: 빌드 도구 없이 정적 HTML에서 바로 쓸 수 있다 (D-66). 보안 헤더(CSP, SEC-12)를 세부화할 때 이 주소를 허용 목록에 넣는다(015).

### R3. 다른 사이트 이미지

- **Decision**: 거른 HTML에서 `src`가 `/files/`로 시작하지 않는 `img`는 지운다.
- **Rationale**: 외부 이미지는 읽는 사람의 IP·방문을 그 사이트에 알리고, 글 목록 썸네일·SNS 미리보기 규칙(BRD-07)과도 맞지 않는다.

## Constitution Check

| 원칙 | 확인 | 결과 |
|---|---|---|
| III. 업로드 검사 | 002와 같은 세 가지 검사·위치 정보 제거 | ✅ |
| IV. ERD·Flyway | V5에 `fk_files_posts` (ON DELETE SET NULL) | ✅ |
| IV. FileStorage | 002의 `LocalFileStorage` 그대로 (SCL-02) | ✅ |
| 권한 거부 테스트 | 비회원 업로드 401, 남의 이미지 연결 안 됨 | ✅ |

## API

| 메서드·주소 | 권한 | 응답 |
|---|---|---|
| `POST /api/files/post-image` (multipart `file`) | 로그인 | 201 `{fileId, url}` |
| `POST /api/files/profile-image` | 로그인(관리자 제외) | 201 `{fileId, url}` (009에서 사용) |

## Project Structure

```text
src/main/resources/db/migration/V5__link_files_to_posts.sql
src/main/java/com/oneblog/post/PostImageExtension.java
src/main/java/com/oneblog/file/   StoredFile(attachTo, markDeleted), StoredFileRepository, FileUploadService, FileController
src/main/java/com/oneblog/common/text/MarkdownRenderer.java  (외부 이미지 제거)
src/main/resources/static/        post-edit.html (Toast UI), js/editor.js
src/test/java/com/oneblog/post/PostImageIntegrationTest.java
```
