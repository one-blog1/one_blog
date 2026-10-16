# Data Model: SNS 공유 2차

DB·ERD·Flyway 변경 없음. 저장하는 데이터 없음(공유 수 집계 안 함, 6.2).

## 글 상세 응답에 더하는 값

`GET /api/posts/{id}` 응답(`PostDetailResponse`)에 한 칸:

| 칸 | 값 | 정하는 규칙 |
|---|---|---|
| `shareScope` | `PUBLIC` \| `LINK` \| `MEMBERS` | 글이 숨김이 아니고 블로그도 숨김이 아닐 때: 공개 → `PUBLIC`, 일부 공개 → `LINK`, 비공개 → `MEMBERS`. 글이나 블로그가 숨김이면(관리자·멤버가 보는 경우) `MEMBERS` |

## 공유 항목 (화면만, 저장 안 함)

| shareScope | 주소 칸·복사 | X | 텔레그램 | 페이스북 | 기기 공유* |
|---|---|---|---|---|---|
| PUBLIC | ✅ | ✅ | ✅ | ✅ | ✅ |
| LINK | ✅ (key 포함) | - | - | - | ✅ (key 포함) |
| MEMBERS | ✅ | - | - | - | - |

\* 기기·브라우저가 공유 창을 지원할 때만.

공유 주소 = 지금 링크 복사와 같은 주소: `{origin}{글 경로}{?key=…}` (key는 지금 화면 주소에 있을 때만).
