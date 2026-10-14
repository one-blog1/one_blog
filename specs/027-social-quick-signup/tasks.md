# Tasks: 소셜 간편 가입 (가입 화면 한 장)

**Input**: `specs/027-social-quick-signup/` (spec.md, plan.md, research.md, data-model.md, contracts/, quickstart.md)

**Tests**: 스펙 성공 기준에 테스트가 있어 포함한다. 이 세션은 Gradle이 막혀 있으므로 브랜치 push 후 CI로 확인한다.

**DB**: 변경 없음. ERD·Flyway 손대지 않음.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일이고 앞 작업에 기대지 않아 함께 할 수 있음
- 경로는 저장소 기준. Java 패키지 `src/main/java/com/oneblog/auth/social/` = `social/`, 테스트 `src/test/java/com/oneblog/auth/social/` = `test/social/`, 정적 파일 `src/main/resources/static/` = `static/`

---

## Phase 1: Setup

- [x] T001 브랜치 `feature/social-quick-signup`이 최신 `develop`(0.18, 37d36eb) 위인지 확인 (`git log origin/develop..HEAD`)

---

## Phase 2: Foundational (모든 이야기가 기대는 것)

- [x] T002 `social/SocialTickets.java`: `Pending` record에 `String phone` 추가(맨 뒤). `issuePending`에서 `putIfText(claims, "phone", ...)`, `readPending`에서 `jwt.getClaimAsString("phone")`. 기존 `new Pending(...)` 호출(SocialProfiles, 테스트) 모두 맞춤
- [x] T003 `social/SocialProfiles.java`: 이름·전화번호 정리 도우미 추가
  - 이름: strip, 1~50자가 아니면 null
  - 전화번호: `+82`로 시작하면 `0`으로 바꾼 뒤 숫자만 남김, 10~11자리가 아니면 null (`010-1234-5678` → `01012345678`, `+82 10-1234-5678` → `01012345678`)
- [x] T004 [P] `social/SocialMasks.java` (새 파일, 패키지 안에서만 씀): 가리기 함수
  - 이름: 1자 그대로, 2자 `홍*`, 3자 이상 첫·끝 글자만 남기고 가운데 `*` (`홍길동` → `홍*동`)
  - 전화번호: 11자리 `010-****-5678`, 10자리 `011-***-5678`
  - 이메일: `@` 앞 2자 + `***@도메인` (`abcdef@naver.com` → `ab***@naver.com`), 앞이 2자 이하면 1자 + `***`
- [x] T005 [P] `test/social/SocialMasksTest.java`: T004 규칙 단위 테스트

**Checkpoint**: 컴파일되고 기존 테스트가 그대로 통과 (Pending 인자 하나 늘어난 것만)

---

## Phase 3: US1 - 받을 수 있는 건 받아 오고, 빈칸만 묻기 (P1) 🎯 MVP

**Goal**: 네이버는 입력 없이(동의만), 구글은 휴대전화, 카카오는 이름·휴대전화만 넣고 가입

**Independent Test**: `/api/auth/social/pending`이 사이트별로 가린 값과 `needName`·`needPhone`을 맞게 주고, `/api/auth/signup/social`이 쿠키 값을 우선해 가입한다

### Tests

- [x] T006 [P] [US1] `test/social/SocialProfilesTest.java`: 네이버 `response.email/name/mobile/nickname` → `emailVerified=true`, phone `01012345678`; 네이버 mobile `+82 10-…` → `010…`; 잘못된 mobile(`1234`) → phone null; 구글 `name` → name; 카카오 이메일 인증 표시 둘 다 true일 때만 verified
- [x] T007 [P] [US1] `test/social/SocialNicknamesTest.java`: 그대로 쓰기(`길동`), 금지 글자 빼기(`길 동!` → `길동`), 13자 이상 → 12자, 2자 미만·금지어(`관리자`) → `회원`+숫자 4자리, 겹치면 `길동2`, `길동2`도 있으면 `길동3`, 12자 닉네임이 겹치면 앞을 줄여 숫자 붙임
- [x] T008 [US1] `test/social/SocialLoginIntegrationTest.java`에 추가:
  - 네이버 pending → `GET /api/auth/social/pending`이 `email: "ab***@…"`, `name: "홍*동"`, `needName:false`, `phone: "010-****-5678"`, `needPhone:false`, `nickname`=네이버 별명
  - 카카오 pending(이름·전화 없음) → `needName:true`, `needPhone:true`
  - 네이버 pending으로 가입할 때 요청 `name`·`phone`이 달라도 저장값은 쿠키 값
  - 카카오 pending에 요청 `phone` 비우면 `400` 필드 오류 `phone`
  - 동의 둘 중 하나라도 false면 `400` (`agreeTerms`/`agreePrivacy`)
  - 요청 닉네임을 바꿔 보내면 바꾼 닉네임으로 저장

### Implementation

- [x] T009 [US1] `social/SocialClientRegistrations.java`: 카카오 `.scope("profile_nickname", "account_email")`
- [x] T010 [US1] `social/SocialProfiles.java`: 네이버 `emailVerified = email != null` (D-139, 네이버 이메일은 믿음), `name`·`mobile`을 T003 도우미로 정리해 Pending에 넣음. 구글 `name`도 T003 이름 정리. 카카오는 phone null. 클래스 주석의 "인증됨만 믿는다"에 네이버 예외를 적음
- [x] T011 [P] [US1] `social/SocialNicknames.java` (새 파일, `@Component`, `SignupPolicy`·`UserRepository` 주입): `String suggest(String hint)`
  - hint가 형식(`^[가-힣A-Za-z0-9]{2,12}$`) 맞고 금지어 아니고 없으면 그대로 (기본은 사이트 닉네임 그대로)
  - 아니면 한글·영문·숫자만 남기고 12자로 자름 → 2자 미만·금지어면 `회원` + 숫자 4자리
  - 겹치면 뒤에 2, 3, … 붙임(합쳐 12자를 넘으면 앞을 줄임), 열 번 안에 못 찾으면 `회원` + 무작위 숫자
- [x] T012 [US1] `social/SocialLoginService.java` `PendingView`: 필드를 `provider, providerLabel, email(가림), emailVerified, emailInUse, name(가림 또는 null), needName, phone(가림 또는 null), needPhone, nickname`으로. `view()`가 T004·T011을 씀. 기존 `suggestNickname` 삭제
- [x] T013 [US1] `social/SocialLoginService.java` `signup()`: 이름은 `pending.name()`이 유효하면 그 값, 아니면 `request.name()`. 전화번호도 `pending.phone()` 우선, 아니면 `normalizePhone(request.phone())`. 이후 검사(`validate`)·저장은 지금 그대로. `SocialSignupRequest`는 그대로 (name·phone은 쿠키에 있으면 무시)
- [x] T014 [US1] `static/social-signup.html`: 가입 폼 위에 "받은 정보" 목록(`id="received"`: 이메일·이름·휴대전화 줄, 값은 `textContent`로) 추가. 이름·휴대전화 입력 칸을 각각 감싸는 요소(`id="name-field"`, `id="phone-field"`)를 둬서 숨길 수 있게. 닉네임 칸·중복 확인·약관 동의는 그대로
- [x] T015 [US1] `static/js/pages/social-signup.js`: `/pending` 결과로 받은 값 줄을 채우고, `needName`/`needPhone`이 false면 해당 칸을 숨기고 `required`를 뺌. 닉네임 칸에 `p.nickname`을 채우고 고칠 수 있게 둠(이미 쓸 수 있는 값이므로 처음엔 중복 확인 통과 상태로 표시). 보낼 때 숨긴 칸은 빈 값으로 보냄. 사용자 값은 `textContent`만 사용 (SEC-06)

**Checkpoint**: 세 사이트 모두 가입 화면 한 장으로 가입 (T008 통과)

---

## Phase 4: US2 - 약관 동의는 처음 온 사람에게만, 가입 화면에서 (P1)

**Goal**: 로그인 화면은 그대로, 처음 온 사람만 가입 화면에서 동의

**Independent Test**: 연결된 소셜 회원은 가입 화면 없이 로그인, 처음 온 사람은 동의 없이 가입 불가

- [x] T016 [US2] `test/social/SocialLoginIntegrationTest.java`: 이미 연결된 소셜 계정이 돌아오면 `/`로 가고 로그인 쿠키가 생기는지(기존 테스트가 있으면 그대로 확인만). 동의 없는 가입 `400`은 T008에서 확인
- [x] T017 [US2] `static/login.html`, `static/js/components/social-login.js`에 변경이 없는지 확인 (동의 체크를 넣지 않음)

---

## Phase 5: US3 - 같은 이메일의 회원이 이미 있을 때 (P1)

**Goal**: 가입하지 않고 "이미 있음" 안내 + 비밀번호로 연결 + 비밀번호 찾기

**Independent Test**: 같은 이메일 회원이 있으면 `emailInUse:true`, 비밀번호만 보내도 연결·로그인

- [x] T018 [US3] `test/social/SocialLoginIntegrationTest.java`: 기존 회원 이메일과 같은 네이버 pending → `/pending`이 `emailInUse:true`와 가린 이메일; `POST /api/auth/social/link`에 `{password}`만 보내면 `200`, `social_accounts`에 연결됨; 틀린 비밀번호는 일반 로그인과 같은 오류; `emailVerified=false`인 pending에서 email 없이 link하면 `400`
- [x] T019 [US3] `social/SocialLoginService.java` `linkWithPassword()`: `email`이 비었으면 `pending.emailVerified()`이고 `pending.email()`이 있을 때만 그 값을 씀, 아니면 `400 VALIDATION_FAILED`(필드 `email`). 넣으면 지금처럼
- [x] T020 [US3] `social/SocialController.java` `LinkRequest`: `email`을 선택값으로 (주석만, 타입 그대로). `toString`은 이메일을 그대로 두지 말고 가린 값 또는 생략
- [x] T021 [US3] `static/social-signup.html` + `static/js/pages/social-signup.js`: `emailInUse`면 가입 폼을 숨기고 "이미 가입한 계정이 있습니다" 문구(가린 이메일 포함) + 비밀번호 칸 + "로그인하고 연결" + 비밀번호 찾기 링크(`/find-password.html` 등 기존 주소 확인). 이메일 칸(`link-email`)은 이 경우 숨기고 보내지 않음. 그 외 경우의 연결 폼은 지금 그대로

---

## Phase 6: Polish & 문서

- [x] T022 [P] `static/privacy.html`: 소셜 수집 항목을 "카카오: 회원 번호·이메일·닉네임 / 네이버: 회원 번호·이메일·이름·휴대전화·별명 / 구글: 회원 번호·이메일·이름"으로, "가입 화면 채우기" 목적 유지. "그 사이트가 인증하지 않은 이메일은 인증번호로 확인" 문장에 네이버는 그대로 쓴다는 내용 반영
- [x] T023 [P] 문서 저장소 `/home/claude/one-blog_docs/docs/requirements/08-decision-log.md`: D-139 추가 (소셜 가입 화면 한 장, 받은 값은 가려 보여 주고 서버가 원래 값 사용, 네이버 이메일 믿음, 닉네임은 사이트 값 기본·고칠 수 있음, 동의는 처음 온 사람만 가입 화면에서, 같은 이메일은 비밀번호 연결)
- [x] T024 [P] 문서 저장소 `docs/requirements/03-functional-requirements.md` USR-01·USR-09, `04-non-functional-requirements.md` 4.6 수집 항목을 D-139에 맞춤
- [x] T025 `social/` 클래스 주석(SocialProfiles, SocialLoginService, SocialTickets)이 바뀐 동작과 맞는지 확인. 주석은 쉬운 한국어, 이모티콘 없음
- [ ] T026 브랜치 push(사용자 확인 뒤) → CI 통과 확인 → 사용자 확인 → `develop`으로 PR. 합친 뒤 `specs/027-social-quick-signup/`을 문서 저장소 `specs/`로 옮기고 상태를 Implemented로

---

## Dependencies

- T002~T004 → 모든 이야기
- US1(T006~T015) → US3(T021은 같은 화면 파일이라 T014·T015 뒤)
- US2는 확인 위주라 US1 뒤 아무 때나
- Polish는 마지막. T023·T024는 코드와 별개로 언제든

## Parallel

- T004·T005 함께
- T006·T007·T011 함께 (다른 파일)
- T022·T023·T024 함께

## Implementation Strategy

1. Phase 1~2 → Phase 3(US1) 끝내고 CI로 확인 (MVP: 가입 화면 한 장)
2. US3(이미 있음) → US2 확인 → 문서
3. 한 브랜치·한 PR로 develop에 합침 (기능 하나 = 스펙 하나)
