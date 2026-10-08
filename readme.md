# One Blog

회원이 블로그를 만들고 함께 운영하는 블로그 플랫폼입니다. 메인블로그에서 회원가입·로그인·블로그 생성·참여를 하고, 실제 글쓰기는 회원이 만든 개별 블로그에서 합니다.

## 문서

이 저장소는 소스 코드만 둡니다(배포용). 요구사항과 기능 명세는 문서 저장소 [one-blog1/one-blog_docs](https://github.com/one-blog1/one-blog_docs)에 있습니다. 코드 주석의 `specs/...`, `docs/...`는 그 저장소의 경로입니다.

- 요구사항 분석서: [docs/requirements](https://github.com/one-blog1/one-blog_docs/tree/main/docs/requirements)
- 기능 로드맵(개발 순서): [docs/roadmap.md](https://github.com/one-blog1/one-blog_docs/blob/main/docs/roadmap.md)
- 기능별 명세: [specs](https://github.com/one-blog1/one-blog_docs/tree/main/specs)
- 프로젝트 원칙(Spec Kit constitution): [.specify/memory/constitution.md](.specify/memory/constitution.md)
- 배포 안내(버전, 환경변수, 확인 방법): [DEPLOY.md](DEPLOY.md)

## 개발 방식

[GitHub Spec Kit](https://github.com/github/spec-kit)으로 기능 단위 명세 → 계획 → 작업 → 구현 순서로 개발합니다. Claude Code에서 아래 명령을 씁니다.

| 순서 | 명령 | 하는 일 |
|---|---|---|
| 1 | `/speckit-specify` | 기능 명세 작성 (요구사항 ID를 근거로) |
| (선택) | `/speckit-clarify` | 모호한 부분을 질문으로 정리 |
| 2 | `/speckit-plan` | 기술 계획, 데이터 모델 작성 |
| 3 | `/speckit-tasks` | 구현 작업 목록으로 쪼개기 |
| (선택) | `/speckit-analyze` | 명세·계획·작업 사이 일관성 점검 |
| 4 | `/speckit-implement` | 작업 목록대로 구현 |

결과물은 기능별로 `specs/<번호>-<기능명>/` 아래에 생깁니다. 기능을 마치면 그 폴더를 문서 저장소의 `specs/`로 옮기고 이 저장소에서는 지웁니다.

## 기술 스택

Spring Boot 4.1 · Java 21 · Spring Security · MySQL 8.4 · Flyway · 정적 HTML + JS · Toast UI Editor (자세한 내용은 요구사항 7장)

## 로컬 실행

필요한 것: JDK 21, MySQL 8.4(테스트용). Spring Boot와 Gradle은 `./gradlew`가 처음 실행할 때 자동으로 받습니다.

- 개발 DB: Crowfoot이 발급한 MySQL(`s4.java21.net:13306`, `cf_u36_d1`). ERD 문서와 연결돼 있고, 테이블은 앱이 시작할 때 Flyway가 만듭니다. Crowfoot의 "변경 반영"으로 테이블을 만들지 않습니다 (constitution IV).
- 테스트 DB: 테스트는 실행마다 테이블을 비우므로 개발 DB와 다른 DB를 씁니다. 기본은 로컬 MySQL의 `one_blog_test`.

1. 테스트 DB와 계정을 만듭니다 (로컬 MySQL root로 한 번만).
   ```sql
   CREATE DATABASE one_blog_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   CREATE USER 'one_blog'@'localhost' IDENTIFIED BY '원하는_비밀번호';
   GRANT ALL PRIVILEGES ON one_blog_test.* TO 'one_blog'@'localhost';
   ```
2. 환경변수 파일을 만들고 값을 채웁니다. `.env`는 저장소에 올라가지 않습니다.
   ```bash
   cp .env.example .env
   ```
   `DB_USERNAME`, `DB_PASSWORD`는 Crowfoot 데이터베이스 탭의 값, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`는 1번에서 만든 로컬 계정입니다.
3. 테스트와 실행
   ```bash
   ./gradlew test
   ./gradlew bootRun
   ```
   http://localhost:8080 에 접속합니다. `MAIL_MODE=log`이면 인증번호가 메일 대신 서버 로그에 찍힙니다.

블로그 대표 이미지 같은 업로드 파일은 `FILE_STORAGE_DIR`(기본 `./uploads`)에 저장되고 저장소에는 올라가지 않습니다. 블로그 생성 개수는 `BLOG_LIMIT_PUBLIC`(기본 3), `BLOG_LIMIT_PRIVATE`(기본 5)로 바꿀 수 있습니다.

검증 시나리오는 문서 저장소의 기능별 quickstart를 봅니다: [001 회원가입·로그인](https://github.com/one-blog1/one-blog_docs/blob/main/specs/001-signup-login/quickstart.md), [002 블로그 생성·목록](https://github.com/one-blog1/one-blog_docs/blob/main/specs/002-blog-create-list/quickstart.md).
