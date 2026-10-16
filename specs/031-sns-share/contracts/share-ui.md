# 화면 계약: 글 공유 (UI 확인 2026-10-16)

## API

`GET /api/posts/{id}?key=…` 응답에 `"shareScope": "PUBLIC" | "LINK" | "MEMBERS"` 추가. 나머지 칸은 그대로.

## 글 화면 (`post.html`)

- `#share-button` (기존 id 유지): 링크 아이콘 → 공유 아이콘, 이름(툴팁·aria-label) "공유". 누르면 가운데 창(`window.modal`, 좁은 폭)을 연다.
- 창 제목: "공유"

### 위: 주소 칸

| 요소 | 내용 |
|---|---|
| `input[data-share="url"]` | 읽기 전용, 공유 주소(일부 공개면 key 포함). aria-label "글 주소" |
| `button[data-share="copy"]` "복사" | 성공: 창을 닫고 `#post-message`에 "링크를 복사했어요." / 자동 복사가 막히면(권한·인앱 브라우저·http): 창을 닫지 않고 주소 칸을 선택해 두고, 칸 아래(`.share-copy-fail`)에 "자동 복사가 안 돼요. 선택된 주소를 직접 복사해 주세요." (UI 확인 2026-10-16) |

### 아래: 아이콘 줄 (가로, 순서대로)

| data-share | 요소 | 이동 | 보이는 조건 |
|---|---|---|---|
| `x` | 링크, 이름 "X" | `https://x.com/intent/tweet?text={제목}&url={주소}` | `PUBLIC` |
| `telegram` | 링크, 이름 "텔레그램" | `https://t.me/share/url?url={주소}&text={제목}` | `PUBLIC` |
| `facebook` | 링크, 이름 "페이스북" | `https://www.facebook.com/sharer/sharer.php?u={주소}` | `PUBLIC` |
| `device` | 버튼, 이름 "기기 공유" | `navigator.share({ title, url })`. 취소는 조용히, 실패는 안내 | `PUBLIC`·`LINK`이고 `navigator.share`가 있을 때 |

- 각 항목: 원형 아이콘(공식 아이콘 파일, 없으면 이름 글자) + 아래 이름. 새 창 링크는 `target="_blank" rel="noopener noreferrer"`, 누르면 창을 닫는다.
- 보이는 아이콘이 없으면 아이콘 줄을 만들지 않는다.
- 안내 한 줄(`.share-note`): `LINK` "링크가 있어야 볼 수 있는 블로그라 SNS에는 올릴 수 없어요.", `MEMBERS` "비공개 블로그라 멤버만 열 수 있어요."
- 모든 글자는 `textContent`(SEC-06), 주소 값은 `encodeURIComponent`. 키보드: Tab 이동, Enter 실행, Esc 닫기.
