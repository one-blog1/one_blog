# One Blog

- 요구사항은 `docs/requirements/`가 기준입니다. 문서끼리 다르면 `08-decision-log.md`의 최신 결정이 우선합니다.
- 프로젝트 원칙은 `.specify/memory/constitution.md`를 따릅니다. 특히 III(보안 기준선)과 IV(처음부터 잡을 구조)는 첫 코드부터 지킵니다.
- 기능 개발은 Spec Kit 순서(`/speckit-specify` → `/speckit-plan` → `/speckit-tasks` → `/speckit-implement`)로 하고, 스펙에는 관련 요구사항 ID를 적습니다.
- DB 구조는 Crowfoot ERD "One Blog 메인블로그"(https://crowfoot.java21.net/workspaces/58/models/655)를 따릅니다. 테이블은 기능을 만들 때 Flyway 마이그레이션으로 추가하고, 구조를 바꿀 때는 ERD를 먼저 고칩니다 (constitution IV).
- 6장에서 상태가 "보류"인 항목은 구현하지 않습니다.
- 요구사항을 바꾸면 해당 장과 8장 결정 기록을 함께 고칩니다.
