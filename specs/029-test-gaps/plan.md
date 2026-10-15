# Implementation Plan: 테스트 보강 (테스트 공백 1~3번)

**Branch**: `feature/test-gaps` | **Date**: 2026-10-15 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/029-test-gaps/spec.md` (2026-10-15 확인, 배치 잠금은 고치기로 결정)

## Summary

테스트만 고치고 더한다.
- **기존 테스트**: 잘못된 이유로 통과하는 7곳을 고친다 (US1).
- **새 테스트**: 보안·권한·개인정보·삭제 배치(US2~US5)와 테스트가 없던 기능(US6)을 확인한다.

기능 코드는 세 파일만 손댄다.

| 파일 | 고치는 내용 | 동작 변화 |
|---|---|---|
| `batch/SchedulerLock.java`, `batch/DailyBatch.java` | 배치가 끝나도 잠금을 최소 5분 유지. 잠금 주인 이름을 앱마다 다르게 (FR-010) | 있음 (스펙에서 결정) |
| `post/HttpVideoThumbnailFetcher.java` | HTTP 클라이언트를 받는 패키지 생성자 하나 추가 (R6) | 없음 (테스트 입구만) |

DB·ERD·Flyway·화면·요구사항 문서는 바꾸지 않는다.

## Technical Context

- **Language/Version**: Java 21, Spring Boot 4.1.1, Spring Security 7
- **Primary Dependencies**: 기존 그대로. 테스트도 이미 있는 것만 쓴다.
  - JUnit 5, MockMvc, Mockito(`@MockitoBean`), AssertJ, JsonPath
  - `MockRestServiceServer`: spring-test에 들어 있다
- **Storage**: MySQL 8.4 테스트 DB(`one_blog_test`). 테스트마다 모든 테이블을 비운다. 저장 파일은 `build/test-uploads`.
- **Testing**:
  - 이름 규칙(028): `...IntegrationTest`는 DB를 쓰고, `...Test`는 단위 테스트다.
  - 이 작업 공간은 Maven 저장소에 접속하지 못해 테스트를 돌릴 수 없다. CI로만 확인한다 (R9).
- **Target Platform**: CI(ubuntu, MySQL 8.4 서비스). 운영 코드 중 배치 잠금은 운영 서버(앱 2개)를 기준으로 고친다.
- **Project Type**: 웹 서비스 (모놀리스, 정적 HTML/JS)
- **Performance Goals**: CI 전체 테스트 단계가 지금(약 5분)보다 5분 넘게 늘지 않는다 (SC-005).
  - 새 Spring 컨텍스트는 `SecurityFeaturesIntegrationTest` 하나뿐이다 (R2).
- **Constraints**:
  - 외부 사이트에 접속하지 않는다. Turnstile은 목, 영상 사이트는 `MockRestServiceServer`나 기존 가짜 fetcher를 쓴다.
  - 시간 규칙은 저장된 시각을 바꿔서 확인한다 (R5).
- **Scale/Scope**: 분석 항목 약 70개. 테스트 70~100개 추가, 기존 테스트 7곳 수정.

## Constitution Check

| 원칙 | 확인 |
|---|---|
| I. 요구사항 기준 | 스펙과 테스트 설명에 요구사항 ID·결정 번호를 적는다. 요구사항은 바꾸지 않는다. 배치 잠금 수정은 이미 있는 SCL-03("한 번만 실행")에 코드를 맞추는 것이라 새 결정이 필요 없다 |
| II. 작은 단위 완성 | 사용자 스토리(US1~US6)마다 따로 CI를 통과시킬 수 있게 커밋을 나눈다 |
| III. 보안 기준선 | 이번 작업의 목적이 보안 기준선의 테스트다. 비밀값은 테스트용 값만 쓴다(`application-test.yml`). 실제 Turnstile 키·소셜 키를 쓰지 않는다 |
| IV. 구조 | DB 변경 없음. `shedlock` 테이블은 그대로 쓴다. 서버 메모리 상태를 새로 두지 않는다. 잠금 주인 이름의 무작위 값은 앱이 뜰 때 한 번 정하는 식별자라 요청 상태가 아니다 |
| V. 단순함 | 새 테스트 라이브러리 없음. 새 클래스는 5개만 (R8) |
| 개발 흐름 | 권한 기능은 "권한 없는 사용자가 요청하면 거부" 테스트를 둔다는 규칙을 이번에 채운다 (US2) |

통과. 위반 없음.

## Project Structure

### Documentation (this feature)

```text
specs/029-test-gaps/
├── spec.md
├── plan.md            # 이 파일
├── research.md        # 테스트 방식 결정 R1~R11
├── data-model.md      # 테스트 데이터와 shedlock (DB 변경 없음)
├── quickstart.md      # CI로 확인하는 법, 규칙을 깨 보는 확인
├── traceability.md    # 구현 때 만든다: 분석 항목 ↔ 테스트 대조표 (FR-009)
└── tasks.md           # /speckit-tasks
```

`contracts/`는 만들지 않는다. 바깥에 보이는 API·화면이 바뀌지 않는다.

### Source Code

```text
src/main/java/com/oneblog/
├── batch/SchedulerLock.java              # FR-010: 최소 유지, 주인 이름, 테스트용 생성자
├── batch/DailyBatch.java                 # FR-010: 시작 시각 + 5분까지 잠금 유지
└── post/HttpVideoThumbnailFetcher.java   # R6: 클라이언트를 받는 패키지 생성자

src/test/java/com/oneblog/
├── IntegrationTestSupport.java           # R1: signUp(email, nickname, name, phone)
├── SecurityFeaturesIntegrationTest.java  # 새: 요청 제한·사람 확인·Secure 쿠키 (US3)
├── CrossBlogPermissionIntegrationTest.java  # 새: 다른 블로그 블로그장 (US2-1)
├── batch/RetentionBatchIntegrationTest.java # 새: 30일·1년 삭제 (US5)
├── batch/SchedulerLockIntegrationTest.java  # 새: 배치 잠금 (US6-12, FR-010)
├── member/SignupPolicyTest.java          # 새 단위: 비밀번호 경계값 (US3-4)
└── (기존 클래스에 더하거나 고침)
    ├── member/SignupIntegrationTest          # US1-2
    ├── blog/BlogPagesIntegrationTest         # US1-3, 화면 주소 (US6-1)
    ├── sanction/ModerationIntegrationTest    # US1-1·4·6, US2-3·4, US4-1, US6-6·7·10
    ├── search/SearchFeedIntegrationTest      # US1-5, US4-4·5
    ├── blog/join/BlogJoinIntegrationTest     # US1-1 (강퇴 대상 번호 분리)
    ├── blog/ops/BlogOpsIntegrationTest       # US2-2·7, US6-1(위임), US6-4·5
    ├── comment/CommentLikeIntegrationTest    # US2-5
    ├── auth/social/SocialLoginIntegrationTest # US2-6, US6-1(가입 그만두기)
    ├── auth/LoginLogoutIntegrationTest       # US3-5 (CSRF 실제 경로)
    ├── account/AccountSecurityIntegrationTest # US3-3
    ├── account/AccountIntegrationTest        # US3-7, US6-1(프로필 사진)
    ├── admin/AdminIntegrationTest            # US4-2, US6-1·2·9
    ├── social/SocialIntegrationTest          # US4-2 (프로필에 개인정보 없음), US6-3(구독 알림)
    ├── notification/NotificationIntegrationTest # US6-1·3
    ├── category/CategoryTagIntegrationTest   # US6-8
    ├── file/FileUploadIntegrationTest        # US6-11 (GPS)
    ├── post/VideoThumbnailIntegrationTest    # US4-6 (2MB)
    ├── post/HttpVideoThumbnailFetcherTest    # US4-6 (리다이렉트, 단위)
    └── common/MaskingTest                    # US4-2 (이름 가림, 단위)
```

**Structure Decision**: 테스트는 기능 패키지별 기존 클래스에 더한다. 한 클래스가 너무 커지는 주제만 새 클래스로 뺀다: 권한 교차, 삭제 배치, 배치 잠금, 보안 기능 켜기.

## 진행 순서

1. **US1**: 테스트 도우미(R1)와 기존 테스트 7곳을 고친다. → CI
2. **US2·US3**: 권한과 로그인·계정. 새 컨텍스트 하나(R2). → CI
3. **US4·US5**: 개인정보와 삭제 배치. `HttpVideoThumbnailFetcher` 생성자 추가. → CI
4. **US6**: 테스트가 없던 기능. → CI
5. **FR-010**: 배치 잠금 테스트를 먼저 올려 실패를 확인하고, 코드를 고쳐 통과를 확인한다. → CI 두 번
6. 대조표(`traceability.md`)를 작성한다. 임시 브랜치에서 규칙을 깨 보는 확인(SC-003)을 하고, 결과를 대조표에 적는다.

새 테스트가 실패하면 R11 절차를 따른다. 코드가 요구사항과 다르면 고치기 전에 사용자에게 묻는다.

## Complexity Tracking

| 예외 | 왜 필요한가 | 더 단순한 안을 버린 이유 |
|---|---|---|
| 기능 코드 파일 3개 수정 | 배치 잠금은 스펙에서 고치기로 정했다. 썸네일은 리다이렉트를 테스트할 입구가 없다 | 리플렉션으로 내부 필드를 바꾸는 테스트는 깨지기 쉽고 읽기 어렵다 |
| 새 Spring 컨텍스트 1개 | 요청 제한·사람 확인·Secure 쿠키는 테스트 설정에서 꺼져 있다 | 기본 설정을 켜면 기존 테스트 수백 개가 영향을 받는다 |

## 이번에 알게 된 것 (범위 밖, 사용자에게 알림)

- **요청 제한이 앱마다 따로**: `RateLimiter`는 기록을 앱 메모리에 둔다. 운영은 앱 2개가 번갈아 받으므로, 같은 IP 로그인 제한이 사실상 2배가 된다. SCL-01과도 어긋난다. 고칠지는 따로 정한다.
