# 대조표: 테스트 공백 분석 ↔ 테스트 (029)

분석(2026-10-15)의 1~3번 항목마다 어느 테스트가 확인하는지 적는다 (FR-009, SC-002). 테스트 경로는 `src/test/java/com/oneblog/` 기준이다.

## 1. 잘못된 이유로 통과하던 기존 테스트 (US1)

| 번호 | 항목 | 근거 | 확인 테스트 | 상태 |
|---|---|---|---|---|
| 1-1 | 테스트 회원 전화번호·이름이 모두 같음 | BLG-11 | `IntegrationTestSupport.signUp(email, nickname, name, phone)`. `ModerationIntegrationTest`·`BlogJoinIntegrationTest`의 강퇴 테스트는 회원마다 다른 번호, 다른 회원은 참여 가능 | 수정 |
| 1-2 | 가입 입력 검사가 오류 배열만 봄 | SEC-02, 6.5 | `SignupIntegrationTest.규칙에_맞지_않는_입력은_400` (한 칸씩, 오류 하나) | 수정 |
| 1-3 | 없는 화면이 4xx만 봄 | SEC-11 | `BlogPagesIntegrationTest.없는_화면은_막힌다` (정확히 401) | 수정 |
| 1-4 | 신고 대상 본인을 권한 없는 멤버로 확인 | D-95 | `ModerationIntegrationTest.신고_대상인_부블로그장은_자기_신고를_보지도_처리하지도_못한다` | 추가 |
| 1-5 | 블로그 태그 검색어가 이름에도 있음 | BLG-03, D-88 | `SearchFeedIntegrationTest.블로그는_이름_소개와_블로그_태그로_찾는다` (태그·소개에만 있는 단어) | 수정 |
| 1-6 | 차단 전 팔로우 확인 없음 | SOC-05 | `ModerationIntegrationTest.차단하면_글과_댓글이_가려지고_팔로우가_끊긴다` | 수정 |
| 1-7 | 상태 코드만 보고 오류 코드를 안 봄 | - | 이번에 손댄·만든 테스트의 거부 응답은 `$.code`도 확인 | 수정 |

## 2. 높음

### 권한 (US2)

| 번호 | 항목 | 근거 | 확인 테스트 | 상태 |
|---|---|---|---|---|
| 2-1 | 다른 블로그의 블로그장 | SEC-07, SEC-11 | `CrossBlogPermissionIntegrationTest` 두 테스트 | 추가 |
| 2-2 | 부블로그장은 블로그장 전용 기능 불가 | 2장, D-71, D-138 | `BlogOpsIntegrationTest.권한을_모두_받은_부블로그장도_블로그장_전용_기능은_못_한다` | 추가 |
| 2-3 | 제재 위계 | 3.7, D-43 | `ModerationIntegrationTest.부블로그장끼리와_자기_자신은_제재할_수_없고_블로그장은_부블로그장을_제재한다` | 추가 |
| 2-4 | 신고 처리 경계 | D-95, D-96 | 1-4, `ModerationIntegrationTest.블로그_신고와_관리자_신고는_서로의_API로_처리할_수_없다` | 추가 |
| 2-5 | 비공개 블로그 좋아요 | 2장 | `CommentLikeIntegrationTest.비공개_블로그의_글은_멤버가_아니면_좋아요를_누를_수_없다` | 추가 |
| 2-6 | 이미 다른 회원에 연결된 소셜 계정 | USR-09, D-126 | 기존 `SocialLoginIntegrationTest.내_정보에서_연결하고_끊는다` (`social_error=taken`). 분석 때 놓친 기존 테스트 | 기존으로 충분 |
| 2-7 | 공유 링크 다시 만들기 | BLG-01 | `BlogOpsIntegrationTest.공유_링크를_다시_만들면_이전_링크로는_들어갈_수_없다` | 추가 |

### 로그인·계정 (US3)

| 번호 | 항목 | 근거 | 확인 테스트 | 상태 |
|---|---|---|---|---|
| 2-8 | 3번 실패 뒤 사람 확인, 관리자 제외 | SEC-13, D-105 | `SecurityFeaturesIntegrationTest.세_번_틀리면_사람_확인을_해야_로그인된다` | 추가 |
| 2-9 | 같은 IP 로그인 10분 30번 (관리자 포함) | SEC-03, D-80 | `SecurityFeaturesIntegrationTest.같은_IP의_로그인은_10분에_30번까지다` | 추가 |
| 2-10 | 이메일 찾기 같은 IP 10분 5번 | USR-08 | `SecurityFeaturesIntegrationTest.이메일_찾기는_같은_IP에서_10분에_5번까지다` | 추가 |
| 2-11 | 재설정 번호 30분·5번, 임시 토큰 10분 | SEC-05, USR-06, USR-08 | `AccountSecurityIntegrationTest`의 `재설정_번호는_30분이_지나면_쓸_수_없다`, `재설정_번호를_다섯_번_틀리면_맞는_번호도_쓸_수_없다`, `이메일_찾기_임시_토큰은_10분이_지나면_쓸_수_없다` | 추가 |
| 2-12 | 재설정하면 잠금 해제 | USR-06 | `AccountSecurityIntegrationTest.잠긴_계정도_비밀번호를_재설정하면_바로_로그인된다` | 추가 |
| 2-13 | 비밀번호 규칙 경계값 (가입·변경·재설정) | SEC-02, 6.7 | `member/SignupPolicyTest`(단위), `AccountIntegrationTest.비밀번호를_바꿀_때도_같은_규칙을_검사한다`, `AccountSecurityIntegrationTest.재설정의_새_비밀번호도_같은_규칙을_검사한다` | 추가 |
| 2-14 | CSRF 실제 쿠키→헤더 경로 | SEC-10 | `auth/CsrfCookieIntegrationTest` (따로 띄운 컨텍스트) | 추가 |
| 2-15 | 쿠키 Secure·HttpOnly·Max-Age | SEC-04 | `SecurityFeaturesIntegrationTest.운영_설정에서_로그인_쿠키는_Secure_HttpOnly다` | 추가 |
| 2-16 | 재확인 10분 | D-102 | `AccountIntegrationTest.재확인은_10분이_지나면_다시_해야_한다` | 추가 |

### 개인정보 (US4)

| 번호 | 항목 | 근거 | 확인 테스트 | 상태 |
|---|---|---|---|---|
| 2-17 | 탈퇴 후 재가입해도 블랙리스트 | BLG-11 | `ModerationIntegrationTest.블랙리스트는_탈퇴_후_다시_가입해도_이메일이나_전화번호로_막는다` | 추가 |
| 2-18 | 가림: 멤버 목록·관리자 상세·프로필·이름 | 4.4 | `BlogOpsIntegrationTest.멤버_목록의_이메일과_전화번호는_가려서_보인다`, `AdminIntegrationTest.회원_목록은_가려서_보이고_전체_보기는_기록이_남는다`(상세 추가), `SocialIntegrationTest.다른_회원의_프로필에는_이메일_이름_전화번호가_없다`, `MaskingTest.이름은_첫_글자와_끝_글자만_남긴다` | 추가 |
| 2-19 | 탈퇴 즉시 정리 | USR-05, 4.5 | `BlogOpsIntegrationTest.탈퇴하면_구독_대기_신청_최근_검색어를_바로_정리한다` | 추가 |
| 2-20 | 검색 제외 (일부 공개·숨김·폐쇄·차단) | 6.1, BLG-01 | `SearchFeedIntegrationTest.검색에는_일부_공개_숨김_폐쇄_블로그와_차단한_회원의_글이_나오지_않는다` | 추가 |
| 2-21 | 피드 범위 | BRD-09, D-37, D-50, D-113 | `SearchFeedIntegrationTest.구독_탭은_볼_수_있는_블로그의_글만_보여_준다`, `정지된_블로그의_글은_내_블로그_새_글에서_빠진다` | 추가 |
| 2-22 | 영상 썸네일 SSRF (리다이렉트·2MB) | D-128 | `HttpVideoThumbnailFetcherTest`(리다이렉트 3개 테스트), `VideoThumbnailIntegrationTest.이_메가바이트를_넘는_썸네일은_버리고_글은_저장된다` | 추가 |

### 삭제 배치 (US5)

| 번호 | 항목 | 근거 | 확인 테스트 | 상태 |
|---|---|---|---|---|
| 2-23 | 폐쇄 30일 정리, 주소 재사용 | BLG-09, D-86 | `batch/RetentionBatchIntegrationTest.폐쇄된_블로그는_30일_뒤_내용을_지우고_주소를_비운다` | 추가 |
| 2-24 | 따로 지운 댓글·파일·업로드·배너 사진 | 4.5 | `RetentionBatchIntegrationTest.따로_지운_댓글과_파일은_30일_뒤_지운다` | 추가 |
| 2-25 | 탈퇴 30일 개인정보 | 4.5 | `RetentionBatchIntegrationTest.탈퇴_회원의_개인정보는_30일_뒤_지운다` | 추가 |
| 2-26 | 1년 기록 | 4.5, D-60 | `RetentionBatchIntegrationTest.신고_제재_관리자_기록은_1년이_지난_것만_지운다` | 추가 |
| 2-27 | 알림 보관·인증번호 정리 | 6.7, 4.5 | `RetentionBatchIntegrationTest.보관_기간이_지난_알림과_쓴_인증번호는_배치가_지운다` (요구사항 "쓰면 바로 삭제"와 다름, 분석 6번) | 추가 |
| 2-28 | 블로그장 경고 1년 | ADM-07, D-30 | `ModerationIntegrationTest.블로그장_경고는_1년_안의_것만_센다` | 추가 |

## 3. 중간 (US6)

| 번호 | 항목 | 근거 | 확인 테스트 | 상태 |
|---|---|---|---|---|
| 3-1 | 관리자 블로그·글 목록, 회원 상세 | ADM-02, ADM-03 | `AdminIntegrationTest.관리자는_블로그와_글을_검색해_본다`, 2-18 | 추가 |
| 3-2 | 관리자 댓글 삭제와 알림 | ADM-03, D-107 | `NotificationIntegrationTest.관리자가_지운_댓글_알림은_사유와_지금_상태의_바로가기를_보여_준다` | 추가 |
| 3-3 | 위임 확인·취소·거절 | BLG-08 | `BlogOpsIntegrationTest.위임_요청을_확인하고_취소하고_거절한다` | 추가 |
| 3-4 | 프로필 사진 올리기 | USR-07 | `AccountIntegrationTest.프로필_사진을_올려_바꾼다` | 추가 |
| 3-5 | 알림 자세히 보기·모두 읽음·설정 조회 | SOC-04, D-107 | 3-2, `NotificationIntegrationTest.모두_읽음과_알림_설정_조회` | 추가 |
| 3-6 | 블랙리스트 문의 거절 | BLG-12 | `ModerationIntegrationTest.블랙리스트_해제_문의를_거절하면_문의한_사람에게_알린다` | 추가 |
| 3-7 | 탈퇴 화면 정보 | USR-05, D-103 | `BlogOpsIntegrationTest.탈퇴_화면은_블로그장인_블로그를_보여_준다` | 추가 |
| 3-8 | 소셜 가입 그만두기 | 022 | `SocialLoginIntegrationTest.가입을_그만두면_가입_대기_정보가_지워진다` | 추가 |
| 3-9 | 글쓰기·수정·관리 화면 주소 | research R10 | `BlogPagesIntegrationTest.글쓰기_글_수정_블로그_관리_화면을_받는다` | 추가 |
| 3-10 | 관리자 활동 기록 | SEC-09, ADM-06 | `AdminIntegrationTest`의 숨김·삭제·공지 테스트에 기록 확인, `ModerationIntegrationTest.관리자의_블로그장_경고와_강제_폐쇄는_기록이_남는다`, `프로필_신고는_관리자에게_가고_관리자는_신고로_글을_지운다`(REPORT_RESOLVE) | 추가 |
| 3-11 | 구독 알림, 관리자 댓글 삭제 알림 | 3.6 | `SocialIntegrationTest.블로그를_구독하면_블로그장에게_알린다`, 3-2 | 추가 |
| 3-12 | 위임 7일 만료·개수 제한·박탈 시 취소 | BLG-08, D-52, D-44 | `BlogOpsIntegrationTest`의 `위임_요청은_7일이_지나면_자동_취소된다`, `블로그_개수_제한에_걸린_멤버는_위임을_받을_수_없다`, `권한을_박탈하면_대기_중인_위임은_취소된다` | 추가 |
| 3-13 | 폐쇄 1일 전 알림, 폐쇄 예정 중 탈퇴 | BLG-09, USR-05 | `BlogOpsIntegrationTest.폐쇄_하루_전에도_알리고_폐쇄_예정_중에는_블로그장이_탈퇴할_수_없다` | 추가 |
| 3-14 | 정지 만료·영구·다른 블로그 | BLG-13, 3.7 | `ModerationIntegrationTest.정지는_기간이_끝나면_풀리고_영구_정지는_풀리지_않고_다른_블로그는_쓴다` | 추가 |
| 3-15 | 먼저 된 부블로그장 승계 | ADM-01, ADM-07 | `ModerationIntegrationTest.블로그장을_내보낼_때_부블로그장이_여럿이면_먼저_된_사람이_이어받는다` | 추가 |
| 3-16 | 부블로그장 권한별 | D-71, D-138 | `CategoryTagIntegrationTest.카테고리는_글_관리_권한이_있는_부블로그장만_관리한다`, `PostIntegrationTest.블로그_공지는_글_관리_권한이_있는_부블로그장도_쓴다`, `ModerationIntegrationTest.신고_목록과_처리는_멤버_관리_권한이_있어야_한다` | 추가 |
| 3-17 | 관리자 열람 범위 | ADM-02, D-106 | `AdminIntegrationTest.관리자는_일부_공개_블로그와_숨긴_글을_보고_기록이_남고_지운_글은_못_본다` | 추가 |
| 3-18 | 신고 나머지 (2주·프로필·글 삭제·당시 내용·여러 신고자) | SOC-06, ADM-04, 6.5 | `ModerationIntegrationTest.신고는_2주가_지나면_다시_할_수_있다`, `프로필_신고는_관리자에게_가고_관리자는_신고로_글을_지운다` | 추가 |
| 3-19 | 업로드 사진 GPS 제거 | 6.3 | `FileUploadIntegrationTest.올린_사진에서_위치_정보를_지운다` | 추가 |
| 3-20 | 04:00 배치 잠금 | SCL-03, FR-010 | `batch/SchedulerLockIntegrationTest` (6개) | 추가 + 코드 수정 |

## 기능 코드 변경 (SC-004)

| 파일 | 내용 | 이유 |
|---|---|---|
| `post/HttpVideoThumbnailFetcher.java` | HTTP 클라이언트를 받는 패키지 생성자 | 리다이렉트·크기 테스트 (research R6). 동작 같음 |
| `batch/SchedulerLock.java`, `batch/DailyBatch.java` | 배치가 끝나도 5분 잠금 유지, 잠금 주인 이름을 앱마다 다르게, 테스트용 생성자 | FR-010 (사용자 결정 A) |

## CI 확인 기록

구현 중 CI 실패는 모두 테스트 쪽 문제였고 기능 코드 버그는 나오지 않았다.

| 회차 | 결과 | 원인과 처리 |
|---|---|---|
| US1 | 1개 실패 | 한 글자 닉네임 → 고침 |
| US2·US3 | 1개 실패 | CSRF 실제 경로: 같은 컨텍스트의 `csrf()`가 CSRF 저장소를 세션용으로 바꿔 둠 → 따로 띄운 컨텍스트로 옮김 |
| US4·US5 | 2개 실패 | 블로그 개수 제한에 걸린 준비 데이터, 미래 시각 배치가 로그인을 지움 → 다시 로그인 |
| US6 | 컴파일 오류 | import 누락 → 고침 |
| FR-010 | 테스트 먼저: 고치기 전 코드에서 `배치를_끝낸_뒤_5분_안에는_다른_서버가_못_잡는다` 실패 확인 → 수정 뒤 통과 |
| 최종 | 전체 378개 + E2E 8개 통과 (보강 전 306개) |

## 규칙을 깨면 실패하는지 (SC-003)

임시 브랜치에서 아래 7가지를 한 커밋으로 깨고 CI 직접 실행 → 모두 해당 테스트가 실패했다 (2026-10-15, 실행 38710457656).

| 깬 것 | 실패한 테스트 |
|---|---|
| 블랙리스트 비교에서 이메일 조건 빼기 | `ModerationIntegrationTest.블랙리스트는_탈퇴_후_다시_가입해도_이메일이나_전화번호로_막는다` |
| 가입 비밀번호 규칙 검사 빼기 | `SignupIntegrationTest.규칙에_맞지_않는_입력은_400` |
| `anyRequest().denyAll()` → `permitAll()` | `BlogPagesIntegrationTest.없는_화면은_막힌다` |
| 신고 목록의 본인 신고 제외 조건 빼기 | `ModerationIntegrationTest.신고_대상인_부블로그장은_자기_신고를_보지도_처리하지도_못한다` |
| 블로그 검색의 블로그 태그 조건 빼기 | `SearchFeedIntegrationTest.블로그는_이름_소개와_블로그_태그로_찾는다` |
| 차단할 때 팔로우 지우기 빼기 | `ModerationIntegrationTest.차단하면_글과_댓글이_가려지고_팔로우가_끊긴다` |
| 블랙리스트 오류 코드 `BLACKLISTED` 바꾸기 | `BlogJoinIntegrationTest.강제_퇴장된_회원은_다시_참여할_수_없다` 외 블랙리스트 테스트 3개 |

## 수치 (SC-002, SC-005)

- 테스트: 통합·단위 306개 → 378개 (+72), E2E 8개 그대로
- CI 전체 테스트 단계: 1분 50초 → 2분 40초 (+50초, 기준 5분 이내)
- 분석 1~3번 항목: 55개 행 모두 확인 테스트 있음 (2-6은 기존 테스트로 충분)
