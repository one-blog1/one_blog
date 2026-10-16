# Research: SNS 공유 2차

## R1. 각 서비스의 공유 주소

- **Decision**:
  - X: `https://x.com/intent/tweet?text={제목}&url={글 주소}`
  - 페이스북: `https://www.facebook.com/sharer/sharer.php?u={글 주소}`
  - 텔레그램: `https://t.me/share/url?url={글 주소}&text={제목}`
  - 값은 모두 `encodeURIComponent`로 넣는다.
- **Rationale**: 세 서비스 모두 주소만으로 공유(글쓰기) 화면을 연다. 앱 키·SDK·외부 스크립트가 필요 없다. X·텔레그램 공식 문서의 형식이다. 페이스북은 sharer.php에 `u`만 넘기고 미리보기는 글 화면의 og 태그(D-66)로 만든다.
- **Alternatives**: 각 서비스의 SDK·위젯 스크립트 → 외부 스크립트가 늘고 CSP(script-src)를 넓혀야 해서 제외(SEC-12, V 단순함).
- 참고: X https://docs.x.com/x-for-websites/post-button/guides/web-intent, 텔레그램 https://core.telegram.org/widgets/share

## R2. 새 창을 여는 방법

- **Decision**: 세 항목은 `<a href="…" target="_blank" rel="noopener noreferrer">` 링크로 만든다. `window.open`을 쓰지 않는다.
- **Rationale**: 사용자가 직접 누르는 링크라 팝업 차단에 걸리지 않고, 키보드·화면 읽기 도구에서도 링크로 읽힌다. `noopener`로 새 창이 원래 화면을 조작하지 못하고, `noreferrer`로 글 주소(일부 공개면 key)가 상대 사이트로 새지 않는다.
- **결과**: 스펙의 "새 창이 막히면 안내"(FR-009)는 일어나지 않으므로 빼고, 링크로 연다는 것으로 바꾼다.
- **Alternatives**: `window.open(url, '_blank', 'noopener')` → noopener를 주면 반환값이 늘 null이라 막혔는지 알 수 없고, 얻는 것이 없다.

## R3. 기기 공유

- **Decision**: 브라우저의 Web Share API(`navigator.share({ title, url })`)를 쓴다. `navigator.share`가 있을 때만 항목을 보인다. 사용자가 취소하면(`AbortError`) 아무 안내도 하지 않고, 그 밖의 실패는 "공유하지 못했어요. 링크 복사를 이용해 주세요."를 보인다.
- **Rationale**: 휴대폰(안드로이드 Chrome, iOS Safari)에서 기기 공유 창을 띄워 인스타그램 DM·문자·카톡 등 설치된 앱으로 보낼 수 있다. HTTPS에서만 동작하고 운영은 HTTPS다. 대부분의 PC 브라우저(특히 리눅스·일부 윈도우 Chrome)는 없어서 숨겨진다.
- **테스트**: CI의 E2E(리눅스 Chromium)에는 `navigator.share`가 없으므로 "보이지 않음"을 확인하고, 보이는 경우는 E2E에서 `navigator.share`를 가짜로 넣어(`addInitScript`) 제목·주소가 넘어가는지 본다.

## R4. 어떤 항목을 보일지 (공개 범위)

- **Decision**: 글 상세 응답에 `shareScope`를 한 칸 더한다. 서버가 정한다.
  - `PUBLIC`: 비회원이 key 없이 볼 수 있는 글 (공개 블로그, 숨김 아님)
  - `LINK`: 일부 공개 블로그의 글 (숨김 아님)
  - `MEMBERS`: 그 밖 (비공개 블로그, 관리자가 숨긴 블로그·글을 멤버·관리자가 보는 경우)
- **Rationale**: 화면은 블로그 공개 범위를 모른다(지금 응답에 없음). key가 주소에 있는지로 추측하면 구독자(key 없이 들어옴)나 숨김 블로그를 틀리게 판단한다. 판단은 서버가 하고 화면은 그 값만 따른다(constitution III: 화면 정보만 믿지 않는다. 여기서는 공유 버튼 표시라 보안 경계는 아니지만 같은 원칙).
- **스펙과의 차이**: 스펙 FR-007의 "서버 API 변경 없음"을 "DB 변경 없음, 글 상세 응답에 한 칸 추가"로 고친다(기존 칸은 그대로라 다른 화면에 영향 없음).
- **Alternatives**: 응답에 블로그 `visibility`를 그대로 내보내기 → 숨김 여부를 화면이 따로 알아야 하고, 내부 상태를 더 드러낸다.

## R5. 공유 창 모양 (UI 확인 2026-10-16)

- **Decision**: 기존 가운데 창(`window.modal`, D-112)을 쓰고 폭을 좁힌다(400px). 제목 "공유". 위에 글 주소가 보이는 읽기 전용 칸과 **복사** 버튼, 아래에 원형 아이콘을 가로로(X → 텔레그램 → 페이스북 → 기기 공유), 아이콘 아래에 이름. "새 창" 같은 설명 글자는 넣지 않는다. 일부 공개·비공개는 숨긴 이유를 한 줄로 안내한다.
- **Rationale**: Esc·창 밖 누르기·닫기·초점 되돌리기가 이미 되어 있고(FR-008), 휴대폰에서 넘치지 않는다. 새 부품을 만들지 않는다(V).
- **Alternatives**: 버튼 옆 작은 펼침 메뉴(popover) → 바깥 누르기·초점 처리를 새로 만들어야 한다.
- 서비스 아이콘은 각 서비스가 브랜드 자료로 주는 **공식 아이콘 파일**을 `static/images/share/`에 두고 쓴다(사용자가 받아 줌). 파일을 받기 전에는 원 안에 서비스 이름 글자를 넣는다. 아이콘을 직접 그려 만들지 않는다. 기기 공유는 사이트 아이콘 세트의 공유 아이콘.

## R6. 버튼 id와 E2E

- **Decision**: 글 화면의 버튼은 id `share-button`을 그대로 두고 글자만 "공유"로 바꾼다. 창 안 요소는 `data-share="copy|x|telegram|facebook|device"`로 구분한다.
- **Rationale**: 기존 테스트·E2E에서 `share-button`을 쓰는 곳은 없지만, id를 유지하면 바꿀 곳이 줄어든다. 새 E2E(`PostShareE2ETest`)를 더한다(CLAUDE.md: 화면 흐름을 바꾸면 E2E도 같이).

## R7. 카카오톡 자리

- **Decision**: 아이콘 줄은 항목 배열로 만들어, 다음 스펙에서 카카오톡 아이콘을 하나 더하면 되게 둔다. 이번에는 카카오 스크립트·CSP·설정을 더하지 않는다.
