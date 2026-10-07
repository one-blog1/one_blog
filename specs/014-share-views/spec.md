# Feature Specification: 공유·조회수

**Feature Branch**: `main` | **Created**: 2026-10-07 | **Status**: Draft

**Input**: "014 공유·조회수 (BRD-07, BRD-11, 6.2)"

**관련 요구사항**: BRD-07, BRD-11, 6.2, 6.7(조회수), D-66, D-77

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 조회수 (Priority: P1)

1. **Given** 블로그 글, **When** 비회원·회원·블로그장이 열면, **Then** 조회수가 1 늘어난다.
2. 같은 사람이 같은 날(한국 날짜) 같은 글을 다시 보면 세지 않는다. 회원은 회원 ID, 비회원은 IP + 브라우저 정보로 사람을 구분한다 (D-77).
3. 이름에 bot·crawler 등이 들어간 수집기(SNS 미리보기 포함)와 관리자의 조회는 세지 않는다.

### User Story 2 - 공유 미리보기 (Priority: P1)

1. **Given** 전체 공개 글(또는 일부 공개 글의 공유 링크), **When** 카톡·X에 링크를 붙이면, **Then** 주소 → 제목(작성자 닉네임까지) → 이미지 순서로 미리보기가 뜬다 (6.2).
2. 이미지는 글의 첫 이미지, 없으면 블로그 대표 이미지.
3. 비공개 블로그의 글이나 key가 틀린 일부 공개 글은 미리보기에 제목도 넣지 않는다.

### Edge Cases

- 링크 복사는 004에서 만든 버튼을 그대로 쓴다. 카카오톡 공유 버튼(Kakao JavaScript SDK)은 카카오 개발자 앱 키 등록이 필요해 키를 받은 뒤 붙인다 (후속).
- 미리보기 이미지는 원본을 쓴다. "줄여서" 넣는 썸네일 생성은 후속.
- 조회 기록은 같은 날 중복만 막으면 되므로 04:00 배치가 이틀 지난 기록을 지운다.

## Requirements *(mandatory)*

- **FR-001**: 조회수는 `post_views`(post_id + viewer_key + view_date UNIQUE)에 INSERT IGNORE가 성공했을 때만 늘린다.
- **FR-002**: viewer_key는 SHA-256 해시만 저장한다(IP·브라우저 정보 원문 저장 없음).
- **FR-003**: og 태그 값은 HTML 특수문자를 바꿔 넣는다 (SEC-06). 사이트 주소는 `PUBLIC_BASE_URL`(없으면 요청 주소).

### Key Entities

- **글 조회 기록(post_views)**: ERD 그대로.

## Success Criteria

- **SC-001**: 같은 사람이 새로고침을 반복해도 하루에 1만 늘어난다.
- **SC-002**: 비공개 글의 제목이 미리보기 HTML에 들어가지 않는다.
