# Feature Specification: 글 안 영상 재생 (유튜브·치지직·SOOP)

**Feature Branch**: `main` | **Created**: 2026-10-12 | **Status**: Draft

**Input**: "글쓰기에서 유튜브 링크 등 영상 링크를 등록하면 글에서 영상 볼 수 있도록", "1,2,3번만(유튜브, 치지직, SOOP) 사용할 수 있도록 적용"

**관련 요구사항**: BRD-01(글 작성·조회), SEC-06(본문 XSS), SEC-12(보안 헤더), 4.6(개인정보 처리방침), D-125(새로 정함)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 영상 링크를 붙이면 글 안에서 재생 (Priority: P1)

1. **Given** 글쓰기, **When** 한 줄에 영상 링크만 붙여 넣고 저장하면, **Then** 글 화면에서 그 자리에 재생기가 글 폭에 맞춰 16:9로 보인다.
2. 지원 링크
   - 유튜브: `youtube.com/watch?v=`, `youtu.be/`, `youtube.com/shorts/`, `youtube.com/live/` (www·m 포함) → `youtube-nocookie.com/embed/{id}`
   - 치지직: 클립 `chzzk.naver.com/clips/{id}`, 다시보기 `chzzk.naver.com/video/{번호}`
   - SOOP: 다시보기 `vod.sooplive.com/player/{번호}` (예전 `vod.sooplive.co.kr`·`vod.afreecatv.com`·`v.afree.ca`도)
3. 문장 중간의 링크, 글자와 주소가 다른 링크, 다른 사이트 링크는 지금처럼 링크로 남는다.

### Edge Cases

- 글쓴이가 직접 쓴 `<iframe>`은 허용한 사이트여도 지운다(기존 거르기). 재생기는 서버가 링크에서 뽑은 영상 번호로만 만든다.
- 목록·검색 미리보기 글자에는 재생기가 끼지 않는다.
- 글쓰기 미리보기(에디터)에서는 링크로 보이고, 저장한 글 화면에서 재생기로 보인다.
- 영상 사이트가 다른 사이트 재생을 막은 영상은 재생기 안에 그 사이트의 안내가 나온다.

## Requirements *(mandatory)*

- **FR-001**: `MarkdownRenderer`가 거른 뒤, 한 줄짜리 영상 링크 문단을 iframe으로 바꾼다(`VideoEmbeds`). iframe은 `sandbox`(재생·전체 화면·새 창만), `loading=lazy`.
- **FR-002**: 보안 헤더 `frame-src`에 세 사이트만 더한다(`VideoEmbeds.FRAME_ORIGINS`와 같게).
- **FR-003**: 개인정보 처리방침 9번에 외부 영상 재생 시 그 사이트로 접속 정보가 간다는 안내를 더한다.
