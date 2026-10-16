# Implementation Plan: SonarQube 첫 분석 정리 (B 범위)

**Branch**: `feature/sonar-cleanup` | **Spec**: [spec.md](spec.md) | **대상**: [issues.md](issues.md)

사용자가 "바로 수정"으로 plan·tasks 확인을 건너뛰게 함(2026-10-16). 이 문서는 한 일의 기록이다.

## 방법

- **동작을 바꾸지 않는다.** 메서드를 옮기고 나누기만 하고, 조건 순서·예외·문구·SQL은 그대로 둔다. 확인은 기존 테스트(unit·전체·E2E)로.
- **새 코드가 새 이슈를 만들지 않게** 한다(품질 게이트). 배열 필드 record는 처음에 평범한 클래스로 바꿨는데, merge 뒤 분석에서 "record로 쓰라"(S6206) 새 이슈 2개로 게이트가 실패했다. 그래서 `Thumbnail`·`Fetched`는 record로 되돌리고 배열 내용으로 비교하는 equals/hashCode/toString을 두고 단위 테스트(`ThumbnailRecordsTest`)로 확인한다. 접근자가 없는 `ImageMetadataStripper.Tiff`는 S6206 대상이 아니라 클래스로 둔다.
- 빌드가 막힌 작업 환경이라 문법 검사만 하고, 컴파일·테스트는 push 뒤 CI(`unit`, 직접 실행 `full`)로 본다.

## 항목별 처리

| 묶음 | 처리 |
|---|---|
| B2 가짜 해시 | `AuthService` 생성자에서 `UUID.randomUUID()`로 만든 값을 bcrypt. 원문 문자열 없음 (SEC-01 그대로) |
| B1·S23 TagService | 두 `findBlogTags`가 각자 `@Transactional(readOnly)`이고 private `queryBlogTags`를 부른다 |
| B3·B4 | `PostPageRenderer.firstImage`: 반복문 안 return 대신 목록 `isEmpty()` |
| B6~B12 COUNT(*) | `AdminService.count(sql, args)` 도우미: null이면 0 |
| B13 | `AccessTokenService`: `getExpiresAt()`를 한 번 읽어 지역 변수로 |
| B14 | `BannerAdminService.validate`: 본문이 없으면 값이 모두 빈 `EMPTY_REQUEST`로 바꿔 한 번에 처리 |
| B24~B26 배치 | `TransactionTemplate.execute` 결과를 `Objects.requireNonNullElse(count, 0)` |
| B27 | `FileStorage.open`이 `Optional<InputStream>`을 돌려줌 |
| B28 | `ViewCounter`: `Objects.requireNonNullElse(ip, "")` |
| B29 | `SocialLoginHandlers`: `isEmpty()`로 먼저 확인 |
| B30 | `BlogPolicy`: 슬러그 정규식 대신 글자 검사 반복문(규칙 같음: a~z·0~9·`-`, 앞뒤 `-`·`--` 금지) |
| B31~B33 배열 record | `Tiff`는 평범한 클래스. `Thumbnail`·`Fetched`는 record + 배열 내용으로 비교하는 equals/hashCode/toString (S6206 새 이슈 2개 정리, `feature/sonar-new-issues`) |
| B34 | `BlogPageController` 수정 화면 경로를 `/posts/*/edit`로 (id를 쓰지 않음) |
| B5·B15~B23 | 오탐 — [hotspots.md](hotspots.md) 2절 |
| S3~S12 복잡도 | 메서드 나누기: `ImageMetadataStripper`(JPEG·WebP, 옛 코드와 56,014개 무작위 입력 결과 비교 0건 차이), 알림 링크(대상별), 배너 검증(도우미 3개), 마크다운 썸네일 영상, 댓글 목록, 로그인 확인(잠금·실패 기록), 프로필 사진, 참여 자동 거절 |
| S13~S22 self-call | 공개 `@Transactional` 메서드 본문을 private 메서드로 옮기고 안에서는 private를 부른다. 바깥 트랜잭션 범위 그대로 |
| S1·S2 빈 블록 | `case "NO_ISSUE"`에 "제재 없이 신고만 닫는다" 주석 |
| H29~H31 정규식 | `common/text/Trim.trailing` (되돌아가기 없는 반복문) |
| H1~H28 | Safe 근거 — [hotspots.md](hotspots.md) 1절 |
