# One Blog

- 이 저장소는 소스 코드만 둡니다(배포용). 요구사항·로드맵·기능 명세(`docs/`, `specs/`)는 문서 저장소 one-blog1/one-blog_docs(https://github.com/one-blog1/one-blog_docs)에 있습니다. 문서를 고칠 때는 그 저장소를 고칩니다.
- 요구사항은 문서 저장소의 `docs/requirements/`가 기준입니다. 문서끼리 다르면 `08-decision-log.md`의 최신 결정이 우선합니다.
- 프로젝트 원칙은 `.specify/memory/constitution.md`를 따릅니다. 특히 III(보안 기준선)과 IV(처음부터 잡을 구조)는 첫 코드부터 지킵니다.
- 기능 개발은 Spec Kit 순서(`/speckit-specify` → `/speckit-plan` → `/speckit-tasks` → `/speckit-implement`)로 하고, 스펙에는 관련 요구사항 ID를 적습니다. 이 저장소에 생긴 `specs/<번호>-<기능명>/`은 기능을 마치면 문서 저장소의 `specs/`로 옮깁니다.
- DB 구조는 Crowfoot ERD "One Blog 메인블로그"(https://crowfoot.java21.net/workspaces/58/models/655)를 따릅니다. 테이블은 기능을 만들 때 Flyway 마이그레이션으로 추가하고, 구조를 바꿀 때는 ERD를 먼저 고칩니다 (constitution IV).
- 6장에서 상태가 "보류"인 항목은 구현하지 않습니다.
- 요구사항을 바꾸면 해당 장과 8장 결정 기록을 함께 고칩니다 (문서 저장소).
- 화면 코드 위치는 `readme.md`의 "코드 위치"를 따릅니다: 스크립트는 `js/lib`(공통)·`js/components`(부품)·`js/pages`(화면별)에 나눠 두고, 날짜·주소 같은 도우미는 `js/lib/ui.js`에만 둡니다. `css/app.css`는 맨 위 주석의 구성 순서대로 그 화면 자리에 넣고 파일 끝에 덧붙이지 않습니다.
- 배포 방법과 업데이트 때 지킬 규칙은 `DEPLOY.md`를 따릅니다.
- 브랜치 규칙 (D-136): 기능은 `develop`에서 `feature/<번호>-<기능명>`을 만들어 작업하고, CI가 통과하면 `develop`에 합친 뒤 그 feature 브랜치를 원격·로컬 모두 지웁니다(`git push origin --delete feature/...`, `git branch -d feature/...`). 버전을 올릴 때만 `develop`을 `main`에 합칩니다(배포는 `main`만). 운영 버그는 `main`에서 `hotfix/<내용>`을 만들어 고친 뒤 `main`과 `develop` 양쪽에 합치고 hotfix 브랜치를 지웁니다. `main`에 바로 커밋하지 않습니다. 문서 저장소는 지금처럼 `main`에 바로 올립니다.
- 버전(`build.gradle.kts`의 `version`)은 사용자가 말할 때만 올립니다. 코드를 바꿔 main에 올리는데 사용자가 버전을 말하지 않았으면, 바뀐 내용에 맞는 버전을 배포마다 0.01씩 올리는 규칙(점 뒤 두 자리, 0.21 → 0.22 → … → 0.30. 문서 저장소 `releases/README.md`, D-121)으로 제안하고 올릴지 먼저 묻습니다. 버전을 올리지 않은 push는 배포되지 않습니다 (D-120).
- 사용자가 채팅에 `update <버전>`(예: `update 0.21`)이라고 하면 `.claude/skills/update/SKILL.md` 순서대로 버전 올리기·배포 확인·문서 저장소 릴리즈 노트·블로그 공지 문구까지 진행합니다.
