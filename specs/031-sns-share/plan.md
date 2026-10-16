# Implementation Plan: SNS 공유 2차

**Branch**: `feature/sns-share` | **Date**: 2026-10-16 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/031-sns-share/spec.md` (2026-10-16 확인. 범위: X·페이스북·텔레그램·기기 공유, 카카오톡은 다음)

## Summary

글 화면의 "링크 복사" 버튼을 **공유** 버튼으로 바꾸고, 누르면 가운데 창에 공유 방법 목록을 띄운다.
- X·페이스북·텔레그램: 각 서비스 공유 주소로 가는 새 창 링크 (키·외부 스크립트 없음, R1·R2)
- 기기 공유: 브라우저 공유 창(`navigator.share`), 지원하는 기기에서만 (R3)
- 보이는 항목은 서버가 글 상세 응답에 넣는 `shareScope`(PUBLIC·LINK·MEMBERS)로 정한다 (R4)

| 파일 | 바꾸는 내용 |
|---|---|
| `post/ShareScope.java` (새) | 공유 범위 enum과 판단 규칙 `of(visibility, blogHidden, postHidden)` |
| `post/dto/PostDetailResponse.java`, `post/PostService.java` | 응답에 `shareScope` 한 칸 |
| `static/js/components/share.js` (새) | 공유 창: 항목 목록 만들기, 링크 복사, 기기 공유 |
| `static/js/pages/post/post.js` | 공유 버튼이 `share.js`를 부른다. 기존 `copyLink`는 `share.js`로 옮김 |
| `static/post.html` | 버튼 글자 "공유"(id 유지), `share.js` 불러오기, 아이콘 |
| `static/js/lib/icons.js` | 공유 아이콘 하나 (필요하면) |
| `static/css/app.css` | 3부 "글(004)" 자리에 공유 목록 모양 |

DB·ERD·Flyway·보안 헤더(CSP)·설정 변경 없음.

## Technical Context

- **Language/Version**: Java 21, Spring Boot 4.1.1 / 화면은 정적 HTML + 바닐라 JS
- **Primary Dependencies**: 새 의존성 없음. 외부 스크립트 없음
- **Storage**: 없음 (DB 변경 없음)
- **Testing**:
  - 단위: `ShareScopeTest` (판단 규칙 표 전체)
  - 통합: `PostIntegrationTest`에 `shareScope` 확인 (공개·일부 공개·비공개·숨김)
  - E2E: `PostShareE2ETest` (quickstart 1~5)
  - 이 작업 공간은 빌드가 막혀 있어 CI(`unit`, 직접 실행 `full`)로 확인
- **Target Platform**: 데스크톱·휴대폰 브라우저. 기기 공유는 HTTPS + 지원 브라우저에서만
- **Project Type**: 웹 서비스 (모놀리스, 정적 HTML/JS)
- **Constraints**: 사용자 글자는 `textContent`(SEC-06), 새 창 링크는 `noopener noreferrer`, 공유 수 저장 안 함(6.2)
- **Scale/Scope**: 새 파일 3(enum, JS 부품, 테스트 2·E2E 1), 고치는 파일 6

## Constitution Check

| 원칙 | 확인 |
|---|---|
| I. 요구사항 기준 | BRD-07, 6.2를 따른다. 6.2의 "인스타그램"을 기기 공유로 대신하는 것과 공개 범위별 항목은 새 결정 D-142로 문서 저장소 6.2·8장에 남긴다 |
| II. 작은 단위 완성 | 카카오톡을 빼고 키 없이 되는 4개만 먼저. US마다 동작 확인 가능 |
| III. 보안 기준선 | 사용자 값은 `textContent`, 주소 값은 인코딩. 외부 스크립트를 더하지 않아 CSP 그대로. `noreferrer`로 key가 상대 사이트에 새지 않음. 일부 공개(key) 글은 공개 SNS 항목을 숨김. 판단은 서버가 함(R4) |
| IV. 구조 | DB 변경 없음. 서버 상태 없음 |
| V. 단순함 | 기존 가운데 창(`window.modal`) 재사용, SDK 없음, 링크로 새 창(R2) |
| 개발 흐름 | 권한이 걸린 기능이 아님(보이는 글에서만 공유). 그래도 공개 범위별 노출은 테스트로 확인 |

통과. 위반 없음.

## Project Structure

### Documentation (this feature)

```text
specs/031-sns-share/
├── spec.md
├── plan.md            # 이 파일
├── research.md        # R1~R7
├── data-model.md      # shareScope, 항목 표
├── quickstart.md      # 확인 방법
├── contracts/
│   └── share-ui.md    # API 한 칸 + 화면 계약
└── checklists/requirements.md
```

### Source Code (repository root)

```text
src/main/java/com/oneblog/post/
├── ShareScope.java                 # 새
├── PostService.java                # detail()에서 shareScope 계산
└── dto/PostDetailResponse.java     # 칸 추가 (withViewCount도 함께)
src/main/resources/static/
├── post.html                       # 버튼 글자·스크립트
├── js/components/share.js          # 새: window.share.open({ title, url, scope, onMessage })
├── js/pages/post/post.js           # 공유 버튼 연결
├── js/lib/icons.js                 # 공유 아이콘
└── css/app.css                     # 3부 글(004) 자리
src/test/java/com/oneblog/
├── post/ShareScopeTest.java        # 새 (단위)
├── post/PostIntegrationTest.java   # shareScope 확인 추가
└── e2e/PostShareE2ETest.java       # 새
```

**Structure Decision**: readme "코드 위치"를 따른다. 공유 창은 다른 화면에서도 쓸 수 있는 부품이라 `js/components`에, 주소 만들기 같은 도우미가 필요하면 `js/lib/ui.js`에만 둔다.

## 스펙에서 고치는 곳 (plan 결과)

- FR-007 "DB·서버 API 변경 없음" → "DB 변경 없음. 글 상세 응답에 `shareScope` 한 칸 추가"(R4)
- FR-009 "새 창이 막히면 안내" → 빼기. 새 창은 링크라 막히지 않는다(R2). 기기 공유 실패 안내만 남김(R3)

## 문서 저장소 (기능을 마칠 때)

- 6.2: 공유 수단 2차 상태(X·페이스북·텔레그램·기기 공유 완료, 인스타그램은 기기 공유로, 카카오톡은 키 준비 뒤)
- 8장 D-142: 공개 범위별 공유 항목, 인스타그램 = 기기 공유, 카카오톡 미룸
- roadmap: 031 줄 추가, "다음에 할 것"에 카카오톡 공유

## Complexity Tracking

없음.
