# 화면 계약: 글 공유

## API

`GET /api/posts/{id}?key=…` 응답에 `"shareScope": "PUBLIC" | "LINK" | "MEMBERS"` 추가. 나머지 칸은 그대로.

## 글 화면 (`post.html`)

- `#share-button` (기존 id 유지): 글자 "공유". 누르면 가운데 창(`window.modal`)을 연다.
- 창 제목: "공유"
- 창 안 목록 순서와 구분 값 (`data-share`):

| data-share | 모양 | 동작 | 보이는 조건 |
|---|---|---|---|
| `copy` | 버튼 "링크 복사" | 글 주소를 복사, `#post-message`에 "링크를 복사했어요." (실패하면 주소를 보여 줌, 지금과 같음). 창을 닫음 | 늘 |
| `x` | 링크 "X" | `https://x.com/intent/tweet?text={제목}&url={주소}` 새 창 | `PUBLIC` |
| `facebook` | 링크 "페이스북" | `https://www.facebook.com/sharer/sharer.php?u={주소}` 새 창 | `PUBLIC` |
| `telegram` | 링크 "텔레그램" | `https://t.me/share/url?url={주소}&text={제목}` 새 창 | `PUBLIC` |
| `device` | 버튼 "기기 공유" | `navigator.share({ title, url })`. 취소는 조용히, 실패는 안내 | `PUBLIC`·`LINK`이고 `navigator.share`가 있을 때 |

- 새 창 링크는 `target="_blank" rel="noopener noreferrer"`.
- 모든 글자는 `textContent`로 넣는다(SEC-06). 주소의 값은 `encodeURIComponent`.
- 키보드: Tab으로 항목 이동, Enter로 실행, Esc로 닫기(기존 창 동작).
