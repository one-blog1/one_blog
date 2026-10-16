# Tasks: 테스트 보강 (029)

**Input**: `specs/029-test-gaps/` (spec.md, plan.md, research.md, data-model.md, quickstart.md)

**규칙**
- 테스트 경로는 `src/test/java/com/oneblog/`, 기능 코드는 `src/main/java/com/oneblog/` 기준이다.
- 테스트 이름 규칙(028): DB를 쓰면 `...IntegrationTest`, 아니면 `...Test`다.
- 메서드 이름은 지금처럼 한글 문장(`_` 구분)으로 짓는다. 설명에 근거 ID를 적는다 (FR-008).
- 거부 응답은 상태 코드와 오류 코드(`$.code`)를 함께 확인한다 (US1-7).
- 시간 규칙은 저장된 시각을 바꾸거나 `dailyBatch.runAll(기준 시각)`로 확인한다 (R5).
- 단계 끝의 "CI"는 이렇게 한다.
  - push 또는 CI 직접 실행으로 전체 테스트를 돌린다 (quickstart 1).
  - 새 테스트가 실패하면 research R11 절차를 따른다. 코드가 요구사항과 다르면 고치기 전에 사용자에게 묻는다.
- `[P]` = 다른 파일이라 동시에 해도 되는 작업

## Phase 1: Setup

- [X] T001 분석 1~3번 항목을 모두 적은 대조표 뼈대를 `specs/029-test-gaps/traceability.md`에 만든다.
  - 열: 번호, 항목, 근거 ID, 확인 테스트(클래스.메서드), 상태(추가/수정/보류), 비고
  - 각 행의 확인 테스트 칸은 그 테스트를 만들 때 채운다.

## Phase 2: Foundational

- [X] T002 `IntegrationTestSupport.java`에 테스트 회원의 이름·전화번호를 정하는 도우미를 더한다 (R1).
  - `signupBody(nickname, name, phone)`과 `signUp(email, nickname, name, phone)`, `signUpAndLogin(email, nickname, name, phone)`을 둔다.
  - 기존 `signUp(email, nickname)`·`signupBody(nickname)`은 "홍길동", "010-1234-5678"로 새 메서드를 부른다. 기존 호출은 바꾸지 않는다.

## Phase 3: US1 - 기존 테스트가 실제 규칙을 확인한다 (P1) 🎯 MVP

**Goal**: 잘못된 이유로 통과하는 기존 테스트 7곳을 고친다.
**Independent Test**: CI 통과. quickstart 3의 규칙 깨기에서 해당 테스트가 실패한다 (T073).

- [X] T003 [US1] `sanction/ModerationIntegrationTest.java` `강제_퇴장하면_블랙리스트에_올라_다시_참여할_수_없고_문의로_해제한다`를 고친다.
  - setUp에서 owner·member·outsider의 전화번호를 서로 다르게 가입시킨다.
  - 이 테스트 안의 `UPDATE users SET phone` 우회는 지운다.
  - 강퇴 뒤 outsider(다른 이메일·다른 번호)는 참여 신청이 201(또는 OPEN 블로그 결과)인지 확인한다.
- [X] T004 [P] [US1] `blog/join/BlogJoinIntegrationTest.java` `강제_퇴장된_회원은_다시_참여할_수_없다`를 고친다.
  - 대상 회원과 다른 회원의 번호를 다르게 가입시킨다.
  - 다른 회원은 그 블로그에 참여할 수 있는지 함께 본다.
- [X] T005 [P] [US1] `member/SignupIntegrationTest.java` `규칙에_맞지_않는_입력은_400`을 세 경우로 나눈다.
  - 경우: 비밀번호만 틀림 / 닉네임만 틀림(`<script>`) / 전화번호만 틀림(`12345`)
  - 각 경우 400 `VALIDATION_FAILED`인지 본다.
  - `fieldErrors`가 정확히 그 칸 하나인지 본다(`$.fieldErrors.length()`=1, `$.fieldErrors[0].field`).
  - 각 경우 users가 0건인지 본다.
- [X] T006 [P] [US1] `blog/BlogPagesIntegrationTest.java` `없는_화면은_막힌다`를 고친다 (R3).
  - 비회원이 `/no-such-page.html`을 열면 정확히 401이다(404가 아님, `anyRequest().denyAll()`).
  - `/login.html`은 200이다.
- [X] T007 [US1] `sanction/ModerationIntegrationTest.java`에 `신고_대상인_부블로그장은_자기_신고를_보지도_처리하지도_못한다`를 더한다 (D-95).
  - 준비: 부블로그장(can_manage_members=1)이 쓴 글을 다른 멤버가 신고한다.
  - 그 부블로그장의 `GET /api/blogs/{slug}/reports`는 200이고 0건이다.
  - 블로그장의 목록에는 1건이 있다.
  - 부블로그장의 resolve는 403 `CANNOT_HANDLE_OWN_REPORT`다.
  - 기존 `블로그_안_신고는_...`의 "대상 본인은 못 본다" 주석은 "권한 없는 멤버"로 바꾼다.
- [X] T008 [P] [US1] `search/SearchFeedIntegrationTest.java` `블로그는_이름_소개와_블로그_태그로_찾는다`를 고친다 (BLG-03, D-88).
  - 이름·소개에 없는 블로그 태그(예: `캠핑`)를 setUp 블로그에 더한다.
  - 그 태그로 검색하면 그 블로그가 나온다.
  - 소개에만 있는 단어로도 찾는다.
- [X] T009 [US1] `sanction/ModerationIntegrationTest.java` `차단하면_글과_댓글이_가려지고_팔로우가_끊긴다`의 차단 전 팔로우 요청을 확인한다.
  - 팔로우 요청에 `andExpect(status().isOk())`를 붙이고, 차단 전 follows가 1건인지 본다.
- [X] T010 [US1] T003~T009에서 손댄 테스트의 거부 응답마다 `$.code`를 확인한다.
  - `BLACKLISTED`, `VALIDATION_FAILED`, `CANNOT_HANDLE_OWN_REPORT` 등 서버가 주는 코드
- [X] T011 [US1] CI로 US1을 확인하고, `traceability.md`의 1번 항목을 채운다.

## Phase 4: US2 - 권한 경계를 넘는 요청은 거부된다 (P1)

**Goal**: 다른 블로그의 블로그장, 부블로그장, 제재·신고·좋아요·소셜 연결·공유 링크의 권한 경계를 확인한다.
**Independent Test**: 각 거부 요청이 정해진 상태·오류 코드로 거부되고, 데이터가 바뀌지 않는다.

- [X] T012 [P] [US2] 새 `CrossBlogPermissionIntegrationTest.java`를 만든다. 준비: A 블로그(블로그장 a, 멤버 m, 글·카테고리), B 블로그(블로그장 b).
  - b가 A에 아래를 하면 모두 403이고 A의 데이터가 그대로다.
    - 글 삭제
    - 카테고리 만들기·바꾸기·지우기
    - 멤버 m 제재
    - 정보 수정
    - 폐쇄
    - 공유 링크 다시 만들기
  - b가 B의 번호를 A 주소에 넣으면 404다.
    - A 주소 + B의 신고 번호로 resolve
    - A 주소 + B의 해제 문의 번호로 release
    - A 주소 + B 멤버의 userId로 제재
- [X] T013 [US2] `blog/ops/BlogOpsIntegrationTest.java`에 `권한을_모두_받은_부블로그장도_블로그장_전용_기능은_못_한다`를 더한다 (2장, D-71, D-138).
  - 부블로그장(세 권한 모두 1)이 아래를 하면 모두 403이다.
    - 자신·다른 멤버의 sub-owner PUT
    - transfer POST
    - close POST·DELETE
    - share-link POST
  - DB의 권한 값이 그대로다.
- [X] T014 [US2] `sanction/ModerationIntegrationTest.java` `제재_권한과_대상을_검사한다`에 더한다 (3.7, D-43).
  - 부블로그장 둘(멤버 관리 권한)이 서로 제재하면 403 `CANNOT_SANCTION`이다.
  - 자기 자신을 제재하면 400 `CANNOT_SANCTION_SELF`다.
  - 블로그장이 부블로그장에게 경고하면 204다.
- [X] T015 [US2] `sanction/ModerationIntegrationTest.java`에 `블로그_신고와_관리자_신고는_서로의_API로_처리할_수_없다`를 더한다 (D-96).
  - 블로그장 글 신고(관리자 큐)의 번호로 블로그 resolve를 하면 404다.
  - 멤버 글 신고(블로그 큐)의 번호로 관리자 resolve를 하면 403 `BLOG_REPORT`다.
- [X] T016 [P] [US2] `comment/CommentLikeIntegrationTest.java`에 `비공개_블로그의_글은_멤버가_아니면_좋아요를_누를_수_없다`를 더한다.
  - 403이고, `post_likes`가 0건이다.
- [X] T017 [P] [US2] (기존 `내_정보에서_연결하고_끊는다`가 이미 확인함, `social_error=taken`. 새 테스트 없음) `auth/social/SocialLoginIntegrationTest.java`에 `이미_다른_회원에_연결된_소셜_계정은_연결할_수_없다`를 더한다 (USR-09, D-126).
  - 같은 구글 sub를 다른 회원이 내 정보 연결이나 비밀번호 연결로 붙이려 하면 409 `SOCIAL_ALREADY_LINKED`다.
  - 연결 수가 그대로다.
- [X] T018 [US2] `blog/ops/BlogOpsIntegrationTest.java`에 `공유_링크를_다시_만들면_이전_링크로는_들어갈_수_없다`를 더한다 (BLG-01).
  - UNLISTED 블로그에서 POST share-link를 하면 새 `shareUrl`을 받는다.
  - 새 key로는 블로그 정보가 보인다.
  - 이전 key로는 안내만 보이고, 블로그 이름이 응답에 없다.
  - PUBLIC 블로그에서 하면 409 `NOT_UNLISTED`다.
- [X] T019 [US2] CI로 US2를 확인하고 대조표를 채운다.

## Phase 5: US3 - 로그인·계정 보안 규칙이 실제로 동작한다 (P1)

**Goal**: 사람 확인·요청 제한·Secure 쿠키·CSRF 실제 경로·재설정·재확인 규칙을 확인한다.
**Independent Test**: 새 컨텍스트 하나(`SecurityFeaturesIntegrationTest`)와 기존 클래스의 테스트가 통과한다.

- [X] T020 [US3] 새 `SecurityFeaturesIntegrationTest.java`를 만든다 (R2).
  - 설정: `@TestPropertySource(properties = {"app.rate-limit.enabled=true", "app.cookie.secure=true"})`
  - 사람 확인 목: `@MockitoBean TurnstileVerifier`
    - `enabled()` = true
    - `verify(token, ip)` = `"ok".equals(token)`
  - 도우미 `fromIp(String)`: MockMvc 요청에 `remoteAddr`를 넣는다. 테스트마다 다른 IP를 쓴다.
- [X] T021 [US3] T020 클래스에 `세_번_틀리면_사람_확인을_해야_로그인된다`를 더한다 (SEC-13, 6.7).
  - 회원으로 3번 틀린다.
  - 4번째에 사람 확인 토큰이 없으면 거부된다(`CAPTCHA_REQUIRED`). 상태 코드는 코드에서 확인한다.
  - 틀린 토큰이면 `LOGIN_FAILED_CAPTCHA`다.
  - 토큰 `ok`와 맞는 비밀번호면 200이다.
  - 관리자 로그인은 3번 틀린 뒤에도 토큰 없이 된다 (D-105).
- [X] T022 [US3] T020 클래스에 `같은_IP의_로그인은_10분에_30번까지다`를 더한다 (SEC-03, D-80).
  - 계정 잠금에 걸리지 않게 없는 이메일로 30번 요청한다. 모두 401이다.
  - 31번째는 429 `TOO_MANY_REQUESTS`이고 `retryAfterSeconds`가 있다.
  - 같은 IP의 관리자 로그인도 429다.
  - 다른 IP는 된다.
- [X] T023 [US3] T020 클래스에 `이메일_찾기는_같은_IP에서_10분에_5번까지다`를 더한다 (USR-08).
  - 6번째가 429다.
- [X] T024 [US3] T020 클래스에 `운영_설정에서_로그인_쿠키는_Secure_HttpOnly다`를 더한다 (SEC-04).
  - 로그인 응답의 `Set-Cookie`에서 `ACCESS_TOKEN`·`REFRESH_TOKEN`에 `Secure`, `HttpOnly`가 있다.
  - 로그인 유지를 끄면 두 쿠키 모두 `Max-Age`가 없다.
  - 로그인 유지를 켜면 두 쿠키 모두 `Max-Age=1209600`이다.
  - 재확인 쿠키는 `Secure`·`HttpOnly`·`Max-Age=600`이다.
  - 가입 티켓 쿠키는 `Secure`다.
- [X] T025 [US3] `account/AccountSecurityIntegrationTest.java`에 재설정 만료 테스트를 더한다 (SEC-05, USR-06, USR-08).
  - `재설정_번호는_30분이_지나면_쓸_수_없다`: 재설정 행의 `expires_at`을 과거로 바꾸면 410이다.
  - `재설정_번호를_다섯_번_틀리면_맞는_번호도_쓸_수_없다`: 5번째 틀림 뒤 맞는 번호도 410이다.
  - `이메일_찾기_임시_토큰은_10분이_지나면_쓸_수_없다`: `account_lookup_tokens.expires_at`을 과거로 바꾸면 reset-code가 410 `LOOKUP_EXPIRED`다.
  - `잠긴_계정도_비밀번호를_재설정하면_바로_로그인된다`: 5번 틀려 잠긴 뒤 재설정하면 새 비밀번호 로그인이 200이다.
- [X] T026 [P] [US3] 새 `member/SignupPolicyTest.java`(단위)에 비밀번호 경계값을 넣는다 (SEC-02, 6.7). `SignupPolicy`의 비밀번호 검사를 바로 부른다.
  - 통과: `Abcd12!x`(8자), `Abcd123!Abcd123`(15자)
  - 거부:
    - 7자, 16자
    - 숫자 없음, 영문 없음, 특수문자 없음
    - 공백 포함
- [X] T027 [P] [US3] 비밀번호 변경·재설정도 같은 규칙인지 확인한다. 특수문자 없는 8자 비밀번호를 보내면 각각 400이고 그 칸의 오류가 온다.
  - 변경: `account/AccountIntegrationTest.java`
  - 재설정: `account/AccountSecurityIntegrationTest.java`
- [X] T028 [P] [US3] `auth/LoginLogoutIntegrationTest.java`에 `CSRF는_쿠키와_같은_값을_헤더로_보내야_통과한다`를 더한다 (SEC-10, R4). `.with(csrf())`는 쓰지 않는다.
  - GET `/api/auth/captcha-config`로 `XSRF-TOKEN` 쿠키를 받는다. HttpOnly가 아니다.
  - 쿠키와 `X-XSRF-TOKEN` 헤더에 같은 값을 넣어 로그인하면 200이다.
  - 헤더가 다른 값이면 403이다.
  - 헤더가 없으면 403이다.
- [X] T029 [P] [US3] `account/AccountIntegrationTest.java`에 `재확인은_10분이_지나면_다시_해야_한다`를 더한다 (D-102).
  - 발급된 `REAUTH_TICKET`을 확인한다.
    - 쿠키 Max-Age가 600이다.
    - JWT `exp - iat`이 600초다. payload를 base64로 풀어 본다.
  - 테스트 JWT 키(`application-test.yml`)로 같은 클레임에 만료가 지난 티켓을 만들어 PUT `/api/me/profile`에 보내면 403 `REAUTH_REQUIRED`다.
- [X] T030 [US3] CI로 US3를 확인하고 대조표를 채운다.

## Phase 6: US4 - 개인정보가 새지 않고 규칙대로 지워진다 (P1)

**Goal**: 블랙리스트 재가입, 가림, 탈퇴 정리, 검색·피드 범위, 썸네일 SSRF 방어를 확인한다.
**Independent Test**: 각 테스트가 통과한다. 개인정보 원문이 응답에 없다.

- [X] T031 [US4] `sanction/ModerationIntegrationTest.java`에 `블랙리스트는_탈퇴_후_다시_가입해도_이메일이나_전화번호로_막는다`를 더한다 (BLG-11, 4.5).
  - 강퇴 → 탈퇴 → `verification_codes` 비우기 → 다시 가입 → 참여 신청.
    - 같은 이메일·다른 번호: 403 `BLACKLISTED`
    - 다른 이메일·같은 번호: 403 `BLACKLISTED`
    - 다른 이메일·다른 번호: 참여된다
- [X] T032 [US4] `blog/ops/BlogOpsIntegrationTest.java`에 `멤버_목록의_이메일과_전화번호는_가려서_보인다`를 더한다 (4.4).
  - 블로그장이 보는 members와 멤버 관리 부블로그장이 보는 members를 모두 확인한다.
  - 정확한 값을 본다(예: `me***@example.com`, `010-****-<끝 4자리>`).
  - 원문 이메일·번호 문자열이 응답 본문에 없다.
- [X] T033 [P] [US4] `admin/AdminIntegrationTest.java`에 `관리자_회원_상세는_가려서_보이고_전체_보기는_기록이_남는다`를 더한다 (4.4, ADM-01).
  - GET `/api/admin/users/{id}`는 이메일·이름·전화번호를 가려서 준다.
  - reveal 뒤에는 원문이고 `VIEW_PERSONAL_INFO` 기록이 남는다.
- [X] T034 [P] [US4] `social/SocialIntegrationTest.java`에 `다른_회원의_프로필에는_이메일_이름_전화번호가_없다`를 더한다 (4.4, SOC-03).
  - `/api/users/{닉네임}` 응답 본문에 그 회원의 이메일·이름·전화번호(원문과 숫자만) 문자열이 없다.
- [X] T035 [P] [US4] `common/MaskingTest.java`(단위)에 `이름은_첫_글자와_끝_글자만_남긴다`를 더한다 (4.4).
  - 2글자, 3글자, 4글자 이상, 영문 이름을 확인한다.
  - 기대값은 `Masking.name`의 지금 규칙(USR-07 화면 `홍*동`)을 따른다.
- [X] T036 [US4] `blog/ops/BlogOpsIntegrationTest.java` `회원탈퇴는_블로그장이면_막고_멤버는_탈퇴한_회원이_된다`에 탈퇴 직후 정리를 더한다 (USR-05, 4.5, D-40).
  - 탈퇴 전에 구독·대기 신청·최근 검색어를 만든다.
  - 탈퇴 직후 `blog_subscriptions`·`recent_searches`가 0건이다.
  - 대기 신청은 CANCELED다.
  - `/api/users/{옛 닉네임}`은 404다.
- [X] T037 [US4] 새 `batch/RetentionBatchIntegrationTest.java`에 `탈퇴_회원의_개인정보는_30일_뒤_지운다`를 더한다 (4.5).
  - 프로필 사진을 올린 회원이 탈퇴한다.
  - `runAll(+29일)`: 개인정보가 남아 있다.
  - `runAll(+31일)`: 아래가 지워진다.
    - name·nickname·phone·bio·profile_image_url이 NULL
    - PROFILE 파일 행이 없음
    - 저장 폴더(`app.file.storage-dir`)의 그 파일이 없음
- [X] T038 [US4] `search/SearchFeedIntegrationTest.java`에 `검색에는_일부_공개_숨김_폐쇄_블로그와_차단한_회원의_글이_나오지_않는다`를 더한다 (6.1, BLG-01).
  - UNLISTED 블로그, is_hidden 블로그, CLOSED 블로그의 글과 블로그가 `/api/search/posts`·`/api/search/blogs`에서 0건이다.
  - 로그인한 회원이 차단한 회원의 글은 검색에서 빠진다.
- [X] T039 [US4] `search/SearchFeedIntegrationTest.java`에 `피드는_볼_수_있는_블로그의_글만_보여_준다`를 더한다 (BRD-09, D-37, D-50, D-113).
  - 구독한 공개 블로그를 PRIVATE으로 바꾸면 멤버가 아닌 구독자의 subscriptions 탭이 0건이다.
  - 링크로 구독한 UNLISTED 블로그 글은 subscriptions 탭에 나온다.
  - 그 블로그에서 정지된 멤버의 myblogs 탭에서는 그 블로그 글이 빠진다.
- [X] T040 [US4] `post/HttpVideoThumbnailFetcher.java`에 패키지 생성자를 더한다 (R6).
  - 형태: `HttpVideoThumbnailFetcher(boolean enabled, RestClient client)`
  - 공개 생성자는 지금 만든 `RestClient`로 이것을 부른다.
  - 기능 동작은 바꾸지 않는다.
- [X] T041 [US4] `post/HttpVideoThumbnailFetcherTest.java`(단위)에 리다이렉트 테스트를 더한다. `MockRestServiceServer.bindTo(RestClient.builder())`로 만든 클라이언트를 T040 생성자에 넣는다 (D-128).
  - 허용 사이트 사진이 허용 밖 주소(`https://evil.example.com/a.jpg`)로 302하면 두 번째 요청을 보내지 않는다.
  - 허용 사이트 안에서 4번 리다이렉트하면 받지 않는다. 3번이면 받는다.
  - 2MB+1바이트 응답은 받지 않는다.
  - 이 셋은 `fetch(key)`나 같은 패키지에서 부를 수 있는 경로로 확인한다.
- [X] T042 [P] [US4] `post/VideoThumbnailIntegrationTest.java`에 `2MB를_넘는_썸네일은_버리고_글은_저장된다`를 더한다 (D-128, 6.3).
  - 가짜 fetcher가 2MB+1바이트를 준다.
  - 글은 201이고, 썸네일이 없고, VIDEO_THUMB 파일 행이 0건이다.
- [X] T043 [US4] CI로 US4를 확인하고 대조표를 채운다.

## Phase 7: US5 - 삭제 배치는 정한 것만, 정한 때에 지운다 (P1)

**Goal**: 30일·1년 삭제와 정리가 정한 대상만, 정한 때에 지우는지 확인한다.
**Independent Test**: `RetentionBatchIntegrationTest`가 통과한다. 29일·364일째에는 남는다.

- [X] T044 [US5] `batch/RetentionBatchIntegrationTest.java`에 `폐쇄된_블로그는_30일_뒤_내용을_지우고_주소를_비운다`를 더한다 (BLG-09, D-86, 4.5).
  - 준비: 글·댓글·카테고리·블로그 태그·구독·사진·블랙리스트·해제 문의가 있는 블로그를 폐쇄한다.
  - `runAll(폐쇄 + 29일)`: 모두 남아 있다.
    - 이때 다른 회원이 같은 주소로 만들면 409 `SLUG_TAKEN`(오류 코드는 코드에서 확인)이다.
  - `runAll(폐쇄 + 31일)`: 위 행이 모두 0건이고, 저장 폴더의 파일이 없다.
    - 이제 같은 주소로 만들면 201이다.
- [X] T045 [US5] 같은 클래스에 `따로_지운_댓글과_파일은_30일_뒤_지운다`를 더한다 (4.5, D-40, D-124, D-132).
  - 답글 없는 댓글을 지우면 31일 뒤 행이 없다.
  - 살아 있는 답글이 달린 댓글은 지워도 남는다.
  - 지운 표시 사진, 글에 붙지 않은 업로드, 쓰지 않는 배너 사진은 29일째에는 남는다. 31일째에는 행과 파일이 없다.
- [X] T046 [US5] 같은 클래스에 `신고_제재_관리자_기록은_1년이_지난_것만_지운다`를 더한다 (4.5, D-60).
  - `reports`·`sanctions`·`admin_actions`의 `created_at`을 364일 전과 366일 전으로 둔 행을 만든다.
  - `runAll(지금)` 뒤 366일 전 행만 없다.
- [X] T047 [US5] 같은 클래스에 `보관_기간이_지난_알림과_쓴_인증번호는_배치가_지운다`를 더한다 (6.7, 4.5).
  - 알림:
    - 30일 설정 회원의 31일 지난 알림은 행이 없고, 29일 지난 알림은 남는다.
    - 7일 설정 회원의 8일 지난 알림은 행이 없다.
  - 인증번호·토큰: 쓴 가입 인증번호, 만료된 재설정 번호, 쓴 이메일 찾기 토큰은 배치 뒤 행이 없다.
  - 메서드 설명에 "요구사항(쓰면 바로 삭제)과 다름, 분석 6번"을 적는다 (R10).
- [X] T048 [P] [US5] `sanction/ModerationIntegrationTest.java`에 `블로그장_경고는_1년_안의_것만_센다`를 더한다 (ADM-07, D-30).
  - 13개월 전 OWNER_WARNING 2건이 있다.
  - 새 경고 1건으로는 권한이 박탈되지 않는다. myRole이 그대로 OWNER다.
- [X] T049 [US5] CI로 US5를 확인하고 대조표를 채운다.

## Phase 8: US6 - 테스트가 없던 기능을 확인한다 (P2)

**Goal**: 테스트가 부르지 않던 API와 결과를 확인하지 않던 기능에 테스트를 붙인다.
**Independent Test**: 각 테스트가 통과한다. 대조표의 US6 행이 모두 채워진다.

- [X] T050 [US6] `admin/AdminIntegrationTest.java`에 관리자 목록·상세·댓글 삭제를 더한다 (ADM-02, ADM-03, D-107).
  - `GET /api/admin/blogs`에 검색어가 맞는 블로그가 나오고, 블로그장 닉네임·멤버 수·글 수가 있다.
  - `GET /api/admin/posts`에 숨긴 글도 나온다.
  - `GET /api/admin/users/{id}`의 기본 정보가 맞다.
  - `POST /api/admin/comments/{id}/delete`(사유 포함):
    - 댓글 목록에서 빠진다.
    - 작성자에게 COMMENT_DELETED 알림이 가고, 알림 자세히 보기에 사유가 있다.
    - 회원 API로 관리자가 글을 지우려 하면 403 `USE_ADMIN_API`다.
- [X] T051 [US6] `blog/ops/BlogOpsIntegrationTest.java`에 `위임_요청을_확인하고_취소하고_거절한다`를 더한다 (BLG-08).
  - 블로그장의 `GET /api/blogs/{slug}/transfer`는 대기 요청을 보여 준다.
  - `DELETE`하면 받는 사람의 목록에서 빠진다.
  - 다시 요청한 뒤 받는 사람이 아닌 사람이 `POST /api/transfer-requests/{id}/reject`를 하면 404다.
  - 받는 사람이 reject하면 블로그장이 그대로이고 블로그장에게 TRANSFER_RESULT 알림이 간다.
- [X] T052 [P] [US6] `account/AccountIntegrationTest.java`에 `프로필_사진을_올려_바꾼다`를 더한다 (USR-07).
  - `POST /api/files/profile-image`(TestImages PNG)는 201이고 id·url을 준다.
  - 재확인 뒤 PUT profile(profileFileId)은 200이다.
  - `/api/me`의 `profileImageUrl`이 그 url이다.
  - 비회원 업로드는 401이다.
- [X] T053 [P] [US6] `notification/NotificationIntegrationTest.java`에 알림 자세히 보기·모두 읽음·설정 조회를 더한다 (SOC-04, D-107).
  - `GET /api/notifications/{id}`:
    - 댓글 알림은 바로가기가 그 댓글이다.
    - 글이 지워지면 바로가기가 없다.
    - 댓글만 지워지면 바로가기가 그 글이다.
    - 남의 알림은 404다.
  - `POST /api/notifications/read-all` 뒤 unread-count가 0이다.
  - `GET /api/me/notification-settings`는 끌 수 없는 항목을 표시하고 보관 기간을 준다.
- [X] T054 [P] [US6] `sanction/ModerationIntegrationTest.java`에 `블랙리스트_해제_문의를_거절하면_문의한_사람에게_알린다`를 더한다 (BLG-12).
  - reject는 204이고 BLACKLIST_RESULT 알림이 간다.
  - 여전히 참여할 수 없다 (403 `BLACKLISTED`).
  - 대기 중에 다시 문의하면 409 `INQUIRY_PENDING`이다.
- [X] T055 [US6] `blog/ops/BlogOpsIntegrationTest.java`에 `탈퇴_화면은_블로그장인_블로그를_보여_준다`를 더한다 (USR-05, D-103).
  - `GET /api/me/withdrawal`에 운영 중·폐쇄 예정 블로그가 나온다.
  - 멤버로만 있는 블로그는 나오지 않는다.
- [X] T056 [P] [US6] `auth/social/SocialLoginIntegrationTest.java`에 `가입을_그만두면_가입_대기_정보가_지워진다`를 더한다 (022).
  - `POST /api/auth/social/cancel`은 204다.
  - 응답이 PENDING 쿠키를 만료시킨다(Max-Age=0).
  - 그 뒤 `/api/auth/social/pending`은 대기 정보가 없다고 답한다.
- [X] T057 [P] [US6] `blog/BlogPagesIntegrationTest.java`에 `글쓰기_글_수정_블로그_관리_화면을_받는다`를 더한다.
  - `/blog/{slug}/write`, `/blog/{slug}/posts/{id}/edit`, `/blog/{slug}/manage`가 비회원에게도 200 HTML이다. 데이터는 API가 지킨다.
- [X] T058 [US6] 관리자 조치마다 활동 기록이 남는지 확인한다 (SEC-09, ADM-06).
  - `admin/AdminIntegrationTest.java`의 기존 숨김·삭제·공지 테스트 끝에 `admin_actions` 행을 더한다.
    - BLOG_HIDE·BLOG_UNHIDE·POST_HIDE·POST_UNHIDE·POST_DELETE·COMMENT_HIDE·COMMENT_DELETE·NOTICE_CREATE·NOTICE_DELETE가 각 1건
  - `sanction/ModerationIntegrationTest.java`에서도 확인한다.
    - REPORT_RESOLVE·OWNER_WARNING·BLOG_FORCE_CLOSE가 각 1건
- [X] T059 [P] [US6] `social/SocialIntegrationTest.java`에 `블로그를_구독하면_블로그장에게_알린다`를 더한다 (3.6).
  - 블로그장에게 BLOG_SUBSCRIBE 알림이 1건 간다.
  - 블로그장이 끄면 만들지 않는다.
- [X] T060 [US6] `blog/ops/BlogOpsIntegrationTest.java`에 위임 세부를 더한다 (BLG-08, D-52, D-44).
  - `위임_요청은_7일이_지나면_자동_취소된다`: `runAll(expires_at + 1분)` 뒤 EXPIRED이고 블로그장에게 알림이 간다.
  - `블로그_개수_제한에_걸린_멤버는_위임을_받을_수_없다`: 공개 블로그 3개를 가진 멤버의 accept는 409 `BLOG_LIMIT_EXCEEDED`다.
  - `블로그장_권한을_박탈하면_대기_위임은_취소된다`는 `sanction/ModerationIntegrationTest.java`에 둔다.
- [X] T061 [US6] `blog/ops/BlogOpsIntegrationTest.java`에 폐쇄 세부를 더한다 (BLG-09, USR-05, D-15, D-18).
  - `폐쇄_하루_전에도_알린다`: `runAll(close_scheduled_at - 1일)` 뒤 BLOG_CLOSING이 하나 더 간다.
  - `폐쇄_예정_블로그의_블로그장은_탈퇴할_수_없고_폐쇄가_끝나면_탈퇴한다`:
    - 폐쇄 예정 중 탈퇴는 409 `OWNED_BLOGS_REMAIN`이다.
    - `runAll(예정 + 1분)` 뒤 탈퇴는 204다.
- [X] T062 [US6] `sanction/ModerationIntegrationTest.java`에 정지 세부를 더한다 (BLG-13, 3.7, D-43).
  - `정지_기간이_끝나면_다시_들어간다`: `suspended_until`을 과거로 바꾸면 200이다.
  - `영구_정지는_풀리지_않는다`: days 없이 SUSPENSION을 주면 `suspended_until`이 먼 미래(또는 영구 표시)이고 403 `MEMBER_SUSPENDED`다.
  - `정지돼도_다른_블로그는_쓴다`: 같은 회원이 다른 블로그 글을 쓰면 201이다.
- [X] T063 [US6] `sanction/ModerationIntegrationTest.java`에 `부블로그장이_여럿이면_먼저_된_사람이_블로그장이_된다`를 더한다 (ADM-01, ADM-07).
  - 부블로그장 둘의 `sub_owner_since`가 다르다.
  - owner-revoke 뒤 먼저 된 사람이 OWNER다.
- [X] T064 [US6] 부블로그장 권한별 허용·거부를 확인한다 (D-71, D-138).
  - `category/CategoryTagIntegrationTest.java`: can_manage_posts가 0이면 카테고리 POST가 403, 1이면 201
  - `post/PostIntegrationTest.java`: 같은 조건으로 블로그 공지 쓰기
  - `sanction/ModerationIntegrationTest.java`: can_manage_members가 0이면 신고 목록이 403, 1이면 200
- [X] T065 [P] [US6] `admin/AdminIntegrationTest.java`에 `관리자는_일부_공개_블로그와_숨긴_글을_보고_기록이_남고_지운_글은_못_본다`를 더한다 (ADM-02, D-106).
  - UNLISTED 블로그를 key 없이 열면 200이고 CONTENT_VIEW(BLOG) 기록이 남는다.
  - 숨긴 글을 열면 CONTENT_VIEW(POST) 기록이 남는다.
  - 지운 글은 404다.
- [X] T066 [US6] `sanction/ModerationIntegrationTest.java`에 신고 세부를 더한다 (SOC-06, ADM-04, 6.5, D-85).
  - `같은_대상은_2주가_지나면_다시_신고한다`: 13일 전이면 409 `ALREADY_REPORTED`, 15일 전이면 201이다.
  - `프로필_신고는_관리자에게_간다`: PROFILE 신고가 관리자 목록에 있고 블로그 목록에는 없다.
  - `관리자가_신고를_처리하며_글을_지운다`: CONTENT_DELETED 뒤 글의 deleted_by가 ADMIN이고 작성자에게 POST_DELETED가 간다.
  - `신고_당시_내용이_남는다`: 글을 지운 뒤에도 신고 목록에 신고 당시 제목이 있다.
  - `여러_신고를_한_번에_처리하면_신고자마다_알린다`: 두 명이 같은 글을 신고하고 한 번 처리하면 REPORT_RESULT가 2건이다.
- [X] T067 [P] [US6] `file/FileUploadIntegrationTest.java`에 `올린_사진에서_위치_정보를_지운다`를 더한다 (6.3).
  - GPS EXIF가 든 JPEG(`TestImages` 또는 `ImageMetadataStripperTest`의 만드는 법)를 올린다.
  - 저장 폴더의 파일과 `/files/{name}` 응답 바이트에 GPS IFD가 없다.
  - 사진 방향 값은 남아 있다.
- [X] T068 [US6] CI로 US6를 확인하고 대조표를 채운다.

## Phase 9: FR-010 - 04:00 배치 잠금 (US6-12, 테스트 먼저)

**Goal**: 배치가 끝나도 잠금을 최소 5분 유지한다. 잠금 주인 이름을 앱마다 다르게 한다.
**Independent Test**: 고치기 전에는 T070이 실패하고, 고친 뒤에는 통과한다.

- [X] T069 [US6] `batch/SchedulerLock.java`에 테스트용 패키지 생성자를 더한다. 동작은 아직 바꾸지 않는다.
  - 형태: `SchedulerLock(NamedParameterJdbcTemplate, String owner)`
  - 기존 공개 생성자에는 `@Autowired`를 붙인다.
- [X] T070 [US6] 새 `batch/SchedulerLockIntegrationTest.java`를 만든다. 서로 다른 owner로 만든 두 잠금 A·B를 쓴다 (SCL-03, R7).
  - `한_쪽이_잡으면_다른_쪽은_못_잡는다`
  - `배치를_끝낸_뒤_5분_안에는_다른_서버가_못_잡는다`: `dailyBatch.scheduled()`(Spring의 잠금으로 잡고 풂)를 부른 직후, B의 `tryLock`이 false다.
    - 고치기 전 코드와 고친 코드 모두에서 컴파일되도록 `scheduled()`만 쓴다.
    - 고치기 전에는 풀 때 `lock_until`이 지금이 되므로 이 테스트가 실패한다.
  - `잠금_시간이_지나면_다른_서버가_잡는다`: `lock_until`을 과거로 바꾸면 B가 true다.
  - `최대_시간이_지나면_풀지_않아도_잡는다`
  - `다른_서버의_잠금은_풀_수_없다`: B의 release가 A의 잠금을 바꾸지 않는다.
  - `잠겨_있으면_예약_실행은_배치를_건너뛴다`:
    - B가 잡은 상태에서 `dailyBatch.scheduled()`를 부르면, 예정이 지난 CLOSING 블로그가 그대로 CLOSING이다.
    - B를 풀고 lock_until을 과거로 바꾼 뒤 부르면 CLOSED다.
  - `매일_새벽_4시_한국_시간에_돈다`: `@Scheduled`의 cron이 `0 0 4 * * *`이고 zone이 `Asia/Seoul`이다.
  - push해서 CI에서 "배치를 끝낸 뒤 5분 안에는 못 잡는다"가 **실패**하는지 확인한다. 결과는 대조표에 적는다.
- [X] T071 [US6] FR-010을 고친다.
  - `batch/SchedulerLock.java`:
    - `release(name, holdUntil)`은 `lock_until = max(지금, holdUntil)`로 두고, `locked_by`가 같을 때만 바꾼다.
    - 기존 `release(name)`은 `release(name, 지금)`으로 남긴다.
    - owner는 `호스트:PID:UUID 앞 8자리`로 한다.
  - `batch/DailyBatch.java`:
    - `MIN_HOLD = Duration.ofMinutes(5)`
    - `scheduled()`가 시작 시각을 기억했다가 `finally`에서 `release(LOCK_NAME, 시작 + MIN_HOLD)`를 부른다.
  - 클래스 설명에 이유(운영 앱 2개, 늦게 온 서버의 두 번째 실행)를 적는다.
- [X] T072 [US6] CI로 T070 전체 통과를 확인하고 대조표를 채운다.

## Phase 10: Polish

- [X] T073 SC-003 규칙 깨기 확인 (quickstart 3).
  - 임시 브랜치 `feature/test-gaps-mutation`에서 7가지 규칙을 한 커밋으로 깬다.
  - CI 직접 실행에서 대조표의 해당 7개 테스트가 모두 실패하는지 본다.
  - 결과를 `specs/029-test-gaps/traceability.md`에 적고 임시 브랜치를 지운다.
- [X] T074 `specs/029-test-gaps/traceability.md`를 마무리한다.
  - 분석 1~3번 항목마다 확인 테스트, 보류(`@Disabled`) 항목과 이유를 적는다.
  - 테스트 수 변화(통합·단위)를 적는다.
  - CI 테스트 단계 시간(전후, SC-005)을 적는다.
- [X] T075 마무리 확인.
  - 기능 코드 변경이 `SchedulerLock`·`DailyBatch`·`HttpVideoThumbnailFetcher` 셋뿐인지 `git diff --stat origin/develop -- src/main`으로 본다 (SC-004).
  - 마지막 CI가 통과하는지 본다.
  - 사용자에게 결과와 `develop`으로 PR을 열지 묻는다.
  - 기능을 마치면 `specs/029-test-gaps/`를 문서 저장소 `specs/`로 옮긴다 (CLAUDE.md).

## Dependencies & Execution Order

- **Phase 1·2**: 맨 먼저 한다. T002는 T003·T004·T031이 쓴다.
- **US1 (Phase 3)**: MVP다. 다른 스토리와 독립이지만, 기존 테스트를 고치므로 먼저 끝내 CI 기준선을 잡는다.
- **US2~US5 (Phase 4~7)**: 서로 독립이다. 같은 파일(`ModerationIntegrationTest`, `BlogOpsIntegrationTest`)에 여러 작업이 몰리므로 그 파일 안에서는 차례로 한다.
- **US6 (Phase 8)**: US1~US5와 독립이다.
- **FR-010 (Phase 9)**: 독립이다. 반드시 T069 → T070(CI에서 실패 확인) → T071 → T072 순서로 한다.
- **Polish (Phase 10)**: 모든 스토리 뒤에 한다. T073은 T003~T009 테스트가 있어야 한다.

## Parallel Opportunities

- **US1**: T004·T005·T006·T008은 파일이 달라 동시에 해도 된다. T003·T007·T009는 같은 파일이라 차례로 한다.
- **US2**: T012(새 클래스)·T016·T017은 동시에 해도 된다.
- **US3**:
  - T020~T024는 같은 새 클래스라 차례로 한다.
  - T026·T027·T028·T029는 파일이 달라 동시에 해도 된다.
- **US4**: T033·T034·T035·T042는 동시에 해도 된다. T040 → T041 순서다.
- **US6**: `[P]` 표시 작업(T052, T053, T054, T056, T057, T059, T065, T067)은 서로 다른 파일이라 동시에 해도 된다.

## Implementation Strategy

1. **MVP**: Phase 1~3(US1)까지 하고 CI를 통과시킨다. 이것만으로도 "초록불인데 규칙이 깨진" 테스트 7곳이 없어진다.
2. **보안 우선**: US2 → US3 → US4 → US5 순서로 단계마다 CI를 통과시킨다.
3. **나머지**: US6 다음 FR-010(테스트 먼저)을 하고, 마지막으로 대조표와 규칙 깨기 확인을 한다.
4. **push 묶기**: 이 작업 공간에서 테스트를 돌릴 수 없으므로 단계마다 한 번씩 push해서 CI를 돌린다. 예상 push 횟수는 다음과 같다.
   - 단계마다 1번, 실패를 고치는 push 몇 번
   - FR-010에서 2번
   - 임시 브랜치에서 1번
