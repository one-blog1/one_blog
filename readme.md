# One Blog

회원이 블로그를 만들고 함께 운영하는 블로그 플랫폼입니다. 메인블로그에서 회원가입·로그인·블로그 생성·참여를 하고, 실제 글쓰기는 회원이 만든 개별 블로그에서 합니다.

## 문서

- 요구사항 분석서: [docs/requirements](docs/requirements/README.md)
- 프로젝트 원칙(Spec Kit constitution): [.specify/memory/constitution.md](.specify/memory/constitution.md)
- 기능 로드맵(개발 순서): [docs/roadmap.md](docs/roadmap.md)

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

결과물은 기능별로 `specs/<번호>-<기능명>/` 아래에 쌓입니다.

## 기술 스택

Spring Boot 4.1 · Java 21 · Spring Security · MySQL 8.4 · Flyway · 정적 HTML + JS · Toast UI Editor (자세한 내용은 요구사항 7장)

## 로컬 실행

필요한 것: JDK 21, MySQL 8.4. Spring Boot와 Gradle은 `./gradlew`가 처음 실행할 때 자동으로 받습니다.

1. DB와 계정을 만듭니다 (MySQL root로 한 번만).
   ```sql
   CREATE DATABASE one_blog CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   CREATE DATABASE one_blog_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   CREATE USER 'one_blog'@'localhost' IDENTIFIED BY '원하는_비밀번호';
   GRANT ALL PRIVILEGES ON one_blog.* TO 'one_blog'@'localhost';
   GRANT ALL PRIVILEGES ON one_blog_test.* TO 'one_blog'@'localhost';
   ```
2. 환경변수 파일을 만들고 값을 채웁니다. `.env`는 저장소에 올라가지 않습니다.
   ```bash
   cp .env.example .env
   ```
3. 테스트와 실행
   ```bash
   ./gradlew test
   ./gradlew bootRun
   ```
   http://localhost:8080 에 접속합니다. `MAIL_MODE=log`이면 인증번호가 메일 대신 서버 로그에 찍힙니다.

검증 시나리오는 [specs/001-signup-login/quickstart.md](specs/001-signup-login/quickstart.md)를 봅니다.
