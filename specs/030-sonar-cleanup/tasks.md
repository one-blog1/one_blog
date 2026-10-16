# Tasks: SonarQube 첫 분석 정리 (B 범위)

- [x] T001 스펙·대상 목록(spec.md, issues.md)
- [x] T002 [US1] 취약점 B2: 가짜 해시를 무작위 값으로 (`AuthService`)
- [x] T003 [US1] BLOCKER B1: `TagService` 트랜잭션
- [x] T004 [US1] 버그 B3·B4·B6~B14·B24~B34 코드 수정
- [x] T005 [US3] self-call S13~S23, 빈 블록 S1·S2
- [x] T006 [US4] 정규식 핫스팟 H29~H31 → `Trim`
- [x] T007 [US2] 복잡도 S3~S12 메서드 나누기 (`ImageMetadataStripper`는 무작위 입력 비교)
- [x] T008 [US4] 핫스팟 판단·오탐 목록·서버 표시 방법 (hotspots.md)
- [ ] T009 push → CI `unit`, 직접 실행 `full`(전체 테스트 + E2E + SonarQube)
- [ ] T010 PR → develop (사용자 확인 뒤), merge 뒤 `develop` 분석에서 등급·게이트 확인
- [ ] T011 사용자: 서버에서 오탐 10개·핫스팟 28개 표시
- [ ] T012 문서 저장소: D-141, 스펙 폴더 옮기기
