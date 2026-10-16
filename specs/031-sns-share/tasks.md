# Tasks: SNS 공유 2차 (031)

**Input**: `specs/031-sns-share/` (spec.md, plan.md, research.md, data-model.md, contracts/share-ui.md, quickstart.md)

**규칙**
- 기능 코드는 `src/main/java/com/oneblog/`, 화면은 `src/main/resources/static/`, 테스트는 `src/test/java/com/oneblog/` 기준이다.
- 테스트 이름 규칙(028): DB를 쓰면 `...IntegrationTest`, 브라우저는 `...E2ETest`, 아니면 `...Test`. 메서드 이름은 한글 문장(`_` 구분).
- 화면 글자는 `textContent`로만 넣는다(SEC-06). 주소 값은 `encodeURIComponent`.
- `css/app.css`는 맨 위 주석 순서대로 3부 "글(004)" 자리에 넣고 파일 끝에 덧붙이지 않는다(CLAUDE.md).
- 커밋 메시지에 BRD-07, 6.2를 적는다. 이 작업 공간은 빌드가 막혀 CI로 확인한다.
- `[P]` = 다른 파일이라 동시에 해도 되는 작업

## Phase 1: Setup

없음 (새 의존성·설정·DB 변경 없음).

## Phase 2: Foundational (모든 스토리가 쓰는 공유 범위)

**Goal**: 글 상세 응답에 `shareScope`가 들어간다 (data-model, R4).

- [ ] T001 [P] `post/ShareScope.java`를 만든다: enum `PUBLIC`, `LINK`, `MEMBERS`와 `static ShareScope of(BlogVisibility visibility, boolean blogHidden, boolean postHidden)`.
  - 규칙(data-model 그대로): "글이 숨김이 아니고 블로그도 숨김이 아닐 때: 공개 → `PUBLIC`, 일부 공개 → `LINK`, 비공개 → `MEMBERS`. 글이나 블로그가 숨김이면 `MEMBERS`"
- [ ] T002 [P] `post/ShareScopeTest.java`(단위): 공개·일부 공개·비공개 × 블로그 숨김 × 글 숨김 전체 조합을 표로 확인한다 (BRD-07, 6.2, D-142).
- [ ] T003 `post/dto/PostDetailResponse.java`에 `ShareScope shareScope` 칸을 맨 끝(`adminViewReason` 앞이 아니라 뒤)에 더하고 `withViewCount`도 넘긴다. `post/PostService.java` `detail()`에서 `ShareScope.of(blog.getVisibility(), blog.isHidden(), post.isHidden())`로 채운다. (T001 뒤)
- [ ] T004 `post/PostIntegrationTest.java`에 `글_상세에_공유_범위가_들어간다`를 더한다: 공개 블로그 글 `PUBLIC`, 일부 공개 블로그 글(key로) `LINK`, 비공개 블로그 글(멤버로) `MEMBERS`, 관리자가 숨긴 글(관리자로) `MEMBERS`. `$.shareScope`로 확인. (T003 뒤)

**Checkpoint**: push → CI `unit`(T002), 직접 실행 `full`(T004).

## Phase 3: US3 - 공유 버튼 하나로 모으기 (P1) 🎯 MVP

**Goal**: 링크 복사 아이콘 버튼이 공유 아이콘 버튼이 되고, 누르면 가운데 창에 링크 복사가 첫 항목으로 있다 (contracts/share-ui.md).
**Independent Test**: 공유 → 창 → 링크 복사 → "링크를 복사했어요.", Esc로 닫힘.

- [ ] T005 [P] [US3] `static/js/lib/icons.js`에 `share` 아이콘(선 아이콘, 다른 아이콘과 같은 굵기·크기)을 더한다.
- [ ] T006 [US3] `static/js/components/share.js`를 만든다: `window.share.open({ title, url, scope, message })`.
  - `window.modal.open({ title: '공유' })`로 창을 띄우고 목록(`ul.share-list`)을 만든다.
  - 항목은 배열로 정의한다(다음 스펙에서 카카오톡 한 줄을 더할 수 있게, R7): `{ key, label, show(scope), render }`.
  - `copy` 항목: 버튼 "링크 복사"(`data-share="copy"`). post.js의 `copyLink` 동작을 그대로 옮긴다(복사 성공 "링크를 복사했어요.", 실패하면 주소를 보여 줌). 누르면 창을 닫고 `message`(글 화면의 `#post-message`)에 안내.
- [ ] T007 [US3] `static/js/pages/post/post.js`: `copyLink`를 지우고 `$('share-button')` 클릭 시 `window.share.open({ title: post.title, url: 글 주소 + keyQuery(), scope: post.shareScope, message: $('post-message') })`.
  - 글 주소 만들기는 지금 `copyLink`와 같다: `window.location.origin + window.location.pathname + keyQuery()`.
- [ ] T008 [US3] `static/post.html`: `#share-button`의 `data-icon="link"` → `data-icon="share"`, 글자(이름) "링크 복사" → "공유". id는 그대로. `js/components/share.js`를 `post.js` 앞에 불러온다. 맨 위 주석에 031을 적는다.
- [ ] T009 [US3] `static/css/app.css` 3부 "글(004)" 자리에 `.share-list` 모양: 세로 목록, 항목은 줄 전체를 누를 수 있는 버튼·링크(버튼과 같은 높이), 가는 구분선, 휴대폰에서 넘치지 않음. 새 색 없이 기존 변수만.

**Checkpoint**: 링크 복사가 창 안에서 지금처럼 된다.

## Phase 4: US1 - X·페이스북·텔레그램 (P1)

**Goal**: `PUBLIC` 글에서 세 서비스 공유 화면이 새 창으로 열린다 (R1, R2).
**Independent Test**: 세 링크의 주소에 글 주소(인코딩)가 들어 있고 새 창(`target=_blank`)이다.

- [ ] T010 [US1] `share.js`에 항목 3개(`show`: `scope === 'PUBLIC'`)를 더한다. 모두 `<a target="_blank" rel="noopener noreferrer">`, 누르면 창을 닫는다.
  - `x` "X": `https://x.com/intent/tweet?text={encode(title)}&url={encode(url)}`
  - `facebook` "페이스북": `https://www.facebook.com/sharer/sharer.php?u={encode(url)}`
  - `telegram` "텔레그램": `https://t.me/share/url?url={encode(url)}&text={encode(title)}`

## Phase 5: US2 - 기기 공유 (P2)

**Goal**: 공유 창을 지원하는 기기에서 기기 공유로 인스타그램 DM 등에 보낸다 (R3).
**Independent Test**: `navigator.share`가 있을 때만 보이고, 누르면 `{ title, url }`이 넘어간다.

- [ ] T011 [US2] `share.js`에 `device` 항목 "기기 공유"(버튼)를 더한다.
  - `show`: `(scope === 'PUBLIC' || scope === 'LINK') && typeof navigator.share === 'function'`
  - 누르면 창을 닫고 `navigator.share({ title, url })`. `AbortError`면 아무것도 안 하고, 다른 오류면 `message`에 "공유하지 못했어요. 링크 복사를 이용해 주세요."

## Phase 6: E2E와 마무리

- [ ] T012 `e2e/PostShareE2ETest.java`를 만든다 (quickstart 1~5). 블로그·글은 `apiCreateBlog`·`apiWritePost`로 준비한다.
  - 공개 글: 공유 → `data-share` copy·x·facebook·telegram 보임, device 안 보임. 세 링크 `href`에 인코딩된 글 주소가 들어 있고 `target=_blank`. 링크 복사 → `#post-message` "링크를 복사했어요."(컨텍스트에 clipboard 권한 부여). Esc로 창이 닫힘.
  - 가짜 기기 공유: `page.addInitScript`로 `navigator.share`를 기록하는 가짜로 넣고 → device 보임 → 누르면 기록된 title·url이 글 제목·주소.
  - 비공개 블로그 글(블로그장으로 로그인): copy만 보임.
  - 일부 공개 블로그 글(공유 링크 key로 비회원 접속): copy만 보임(가짜 `navigator.share`면 device도), x·facebook·telegram 없음.
  - clipboard 권한이 필요하면 `E2ETestSupport.newPage`에 옵션을 더하지 말고 이 테스트에서 `page.context().grantPermissions(...)`를 쓴다.
- [ ] T013 push → CI `unit`, 직접 실행 `full`(전체 테스트 + E2E). 실패하면 고친다.
- [ ] T014 사용자 확인 뒤 PR → `develop`. merge 뒤 `develop` 품질 게이트 확인 (게이트 실패면 Annotations 보고 새 브랜치에서).
- [ ] T015 문서 저장소: 6.2 상태(2차 중 X·페이스북·텔레그램·기기 공유 완료, 인스타그램은 기기 공유, 카카오톡은 키 준비 뒤), 8장 D-142, roadmap에 031 줄과 "다음에 할 것"에 카카오톡 공유, 스펙 폴더 옮기기.

## Dependencies

- T001 → T003 → T004. T002는 T001과 함께.
- Phase 3(T005~T009)은 T003의 `shareScope`를 쓰지만, 화면은 값이 없으면 링크 복사만 보이므로 T003과 동시에 해도 된다.
- T010·T011은 T006(목록 틀) 뒤. T010과 T011은 같은 파일이라 차례로.
- T012는 T003~T011 뒤.

## Parallel

- T001·T002·T005는 서로 다른 파일이라 동시에.
- T008(html)·T009(css)는 T006과 동시에 해도 된다.

## Implementation Strategy

1. MVP: Phase 2 + Phase 3 (공유 버튼 → 창 안 링크 복사). 이것만으로도 지금 기능을 잃지 않는다.
2. Phase 4로 X·페이스북·텔레그램, Phase 5로 기기 공유를 더한다.
3. Phase 6에서 E2E로 전체를 묶어 확인하고 PR.
