# 메인블로그 요구사항 분석서

작성일 2026-10-02 · 결정 기록 최종 갱신 2026-10-07

팀의 최신 요구사항 분석서(2026-10-07 기준, D-95까지 반영)를 장별 마크다운으로 옮긴 것입니다. **앞으로는 이 마크다운 파일을 기준으로 고칩니다.**

| 파일 | 내용 |
|---|---|
| [01-overview.md](01-overview.md) | 1. 개요, 서비스 구조 |
| [02-users-and-permissions.md](02-users-and-permissions.md) | 2. 사용자 유형과 권한 |
| [03-functional-requirements.md](03-functional-requirements.md) | 3. 기능 요구사항 (USR, BLG, SOC, BRD, ADM, 알림, 제재 권한) |
| [04-non-functional-requirements.md](04-non-functional-requirements.md) | 4. 비기능 요구사항 (보안, 성능, 확장성, 마스킹, 보관, 개인정보) |
| [05-scope-and-open-items.md](05-scope-and-open-items.md) | 5. 메인과 개별 블로그의 범위, 결정 필요 사항 |
| [06-detailed-policies.md](06-detailed-policies.md) | 6. 세부 정책 (숫자와 규칙) |
| [07-implementation.md](07-implementation.md) | 7. 구현 방식 |
| [08-decision-log.md](08-decision-log.md) | 8. 결정 기록 (D-01 ~ D-95) |

## 읽는 순서

- 무엇을 만드는지: 1 → 2 → 3장
- 지켜야 할 규칙: 4장(보안·비기능), 6장(숫자)
- 어떻게 만드는지: 7장
- 왜 그렇게 정했는지: 8장

## 요구사항 ID

| 접두어 | 모듈 |
|---|---|
| USR | 회원 |
| BLG | 블로그 |
| SOC | 소셜 |
| BRD | 게시판 |
| ADM | 메인 관리자 |
| SEC | 보안 |
| SCL | 확장성(서버 이중화 대비) |
| D-nn | 결정 기록 |

## 고치는 규칙

- **6장**: 상태가 확정인 항목은 그대로 구현하고, 보류인 항목은 팀에서 더 정합니다. 정하면 상태를 확정으로 바꾸고, 추천안과 다르게 정했으면 추천안 칸을 고칩니다.
- **8장**: 결정하거나 바꿀 때마다 표 맨 위에 한 줄씩 추가합니다. 바꾼 경우에는 이전 내용과 바꾼 이유를 함께 적습니다.
- 다른 장을 고쳤다면 8장에도 그 결정을 남깁니다.
