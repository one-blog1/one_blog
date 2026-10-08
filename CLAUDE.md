# One Blog

- 이 저장소는 소스 코드만 둡니다(배포용). 요구사항·로드맵·기능 명세(`docs/`, `specs/`)는 문서 저장소 one-blog1/one-blog_docs(https://github.com/one-blog1/one-blog_docs)에 있습니다. 문서를 고칠 때는 그 저장소를 고칩니다.
- 요구사항은 문서 저장소의 `docs/requirements/`가 기준입니다. 문서끼리 다르면 `08-decision-log.md`의 최신 결정이 우선합니다.
- 프로젝트 원칙은 `.specify/memory/constitution.md`를 따릅니다. 특히 III(보안 기준선)과 IV(처음부터 잡을 구조)는 첫 코드부터 지킵니다.
- 기능 개발은 Spec Kit 순서(`/speckit-specify` → `/speckit-plan` → `/speckit-tasks` → `/speckit-implement`)로 하고, 스펙에는 관련 요구사항 ID를 적습니다. 이 저장소에 생긴 `specs/<번호>-<기능명>/`은 기능을 마치면 문서 저장소의 `specs/`로 옮깁니다.
- DB 구조는 Crowfoot ERD "One Blog 메인블로그"(https://crowfoot.java21.net/workspaces/58/models/655)를 따릅니다. 테이블은 기능을 만들 때 Flyway 마이그레이션으로 추가하고, 구조를 바꿀 때는 ERD를 먼저 고칩니다 (constitution IV).
- 6장에서 상태가 "보류"인 항목은 구현하지 않습니다.
- 요구사항을 바꾸면 해당 장과 8장 결정 기록을 함께 고칩니다 (문서 저장소).
- 배포 방법과 업데이트 때 지킬 규칙은 `DEPLOY.md`를 따릅니다.
- 버전(`build.gradle.kts`의 `version`)은 사용자가 말할 때만 올립니다. 코드를 바꿔 main에 올리는데 사용자가 버전을 말하지 않았으면, 바뀐 내용에 맞는 버전을 주.부.수 규칙(문서 저장소 `releases/README.md`)으로 제안하고 올릴지 먼저 묻습니다. 버전을 올리지 않은 push는 배포되지 않습니다 (D-120).
- 사용자가 채팅에 `update <버전>`(예: `update 0.2.0`)이라고 하면 `.claude/skills/update/SKILL.md` 순서대로 버전 올리기·배포 확인·문서 저장소 릴리즈 노트·블로그 공지 문구까지 진행합니다.
