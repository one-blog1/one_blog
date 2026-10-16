# 030 핫스팟 검토와 서버 표시 목록

0.20 분석 기준(줄 번호는 분석 시점). 이 문서를 근거로 SonarQube 서버(`one-blog`)에서 사용자가 표시한다.

## 1. 보안 핫스팟 31개 판단

### 코드로 없앤 것 (3)

| # | 위치 | 처리 |
|---|---|---|
| H29 | `auth/social/SocialClientRegistrations.java:35` | 끝의 `/` 지우기 정규식 → `Trim.trailing(..., "/")`로 바꿈. 다음 분석에서 사라짐 |
| H30 | `common/text/MarkdownRenderer.java:128` | 주소 끝 문장부호 지우기 정규식 → `Trim.trailing(..., ".,!?)]")` |
| H31 | `post/PostPageRenderer.java:49` | 기본 주소 끝 `/` 지우기 정규식 → `Trim.trailing(..., "/")` |

`common/text/Trim`은 뒤에서부터 한 글자씩 보는 반복문이라 되돌아가기(backtracking)가 없다. 결과는 정규식(`[문자]+$` 지우기)과 같다.

### Safe로 표시할 것 (28)

**비밀번호 의심 (S2068) 3개: 비밀번호가 아니다**

| # | 위치 | 근거 |
|---|---|---|
| H1 | `mail/MailTexts.java:12` | `PASSWORD_RESET_SUBJECT`는 메일 제목 문구("비밀번호 재설정 인증번호") |
| H2 | `member/SignupService.java:88` | `"password"`는 입력 오류를 돌려줄 필드 이름 |
| H3 | `member/User.java:105` | `NO_PASSWORD = "!SOCIAL"`은 소셜 전용 회원 표시값. bcrypt 해시(`$`로 시작)가 아니라 어떤 비밀번호와도 맞지 않는다 (D-126) |

**난수 (S2245) 1개**

| # | 위치 | 근거 |
|---|---|---|
| H28 | `auth/social/SocialNicknames.java:77` | 소셜 가입 때 겹치는 닉네임 뒤에 붙이는 숫자. 비밀·토큰이 아니고 맞혀도 얻는 것이 없다. 토큰은 `TokenHasher.randomToken`(SecureRandom)을 쓴다 |

**SQL 조각 이어 붙이기 (S2077) 24개: 붙이는 조각은 모두 코드에 고정된 문자열이고, 사용자 값은 모두 `:이름` 바인딩**

| # | 위치 | 붙이는 조각 | 근거 |
|---|---|---|---|
| H4·H5 | `admin/AdminService.java:74·75` (회원 목록) | `where` | 텍스트 블록 상수. 검색어는 `:q`(`AdminQueries.likePattern`), 페이지는 `:limit`·`:offset` |
| H6·H7 | `admin/AdminService.java:141·142` (블로그 목록) | `where` | 문자열 상수. 검색어 `:q` |
| H8·H9 | `admin/AdminService.java:178·179` (글 목록) | `where` | 텍스트 블록 상수. 검색어 `:q` |
| H10·H11 | `admin/AdminService.java:224·225` (댓글 목록) | `where` | 텍스트 블록 상수. 검색어 `:q` |
| H12·H13 | `batch/PurgeTask.java:133·134` (파일 정리) | `where` | 텍스트 블록 상수. 기준 시각 `:cutoff`. 사용자 입력 없음(배치) |
| H14·H15 | `blog/ops/BlogMemberStatsService.java:86·87` (멤버 글) | `where` | 문자열 상수. `:blogId`·`:userId` |
| H16·H17 | `blog/ops/BlogMemberStatsService.java:101·102` (멤버 댓글) | `where` | 문자열 상수. `:blogId`·`:userId` |
| H18·H19 | `notification/NotificationQueryService.java:91·98` (알림 목록) | `where` = `MINE` (+ `" AND n.tab = :tab"`) | 둘 다 상수. 탭 값은 `:tab` 바인딩 |
| H20·H21 | `report/ReportService.java:349·352` (같은 대상 신고 일괄 처리) | `sameQueue` | `"blog_id IS NULL"` 또는 `"blog_id = :blogId"` 둘 중 하나(삼항). 값은 바인딩 |
| H22·H23 | `search/PagedIds.java:24·33` (검색·피드 페이지) | `idColumn`, `fromWhere`, `orderBy` | 호출하는 곳 3곳(`SearchService` 2, `FeedService` 1)이 모두 상수 조각을 넘긴다. `orderBy`는 `switch`로 고른 상수, 검색어는 `:ft`·`:tagId`, 차단 조건은 `BlockSearchFilter`의 상수(`:blockViewerId`). 클래스 주석에도 규칙을 적어 둠 (constitution III) |
| H24·H25 | `social/FollowService.java:105·112` (팔로워·팔로잉) | `where`, `join` | 호출은 두 곳뿐이고 둘 다 상수(`"f.followee_id = :userId"` 등) |
| H26·H27 | `tag/TagPostService.java:63·70` (태그 글 목록) | `fromWhere` | `FROM` 상수 + `BlockSearchFilter` 상수 조건. 태그는 `:tagId` |

## 2. 버그 34개 처리

| 처리 | 항목 |
|---|---|
| 코드 수정 (24) | B1·B2(BLOCKER), B3·B4, B6~B14, B24~B34 |
| 오탐 (10) | B5, B15~B23 (아래) |

### 오탐으로 표시할 버그 (S2259, 10개)

모두 `NamedParameterJdbcTemplate.queryForList`(또는 `JdbcTemplate.queryForList`)의 결과 목록을 null이라고 본 경고다. Spring은 이 메서드에서 결과가 없으면 **빈 목록**을 돌려주고 null을 돌려주지 않는다(`RowMapperResultSetExtractor`가 항상 `ArrayList`를 만든다). 코드를 바꾸면 오히려 쓸모없는 null 검사가 생겨 다른 경고(S2583)가 난다.

| # | 위치 | 변수 |
|---|---|---|
| B5 | `account/PasswordResetService.java:177` | `ids` |
| B15 | `banner/SiteSettings.java:22` | `values` |
| B16 | `batch/PurgeTask.java:89` | `purgePosts(...)`에 넘기는 `queryForList` 결과 |
| B17 | `batch/PurgeTask.java:120` | `roots` |
| B18 | `batch/PurgeTask.java:141` | `ids` |
| B19 | `blog/BlogAccessService.java:110` | `reasons` |
| B20 | `blog/BlogAccessService.java:120` | `roles` |
| B21 | `sanction/BlacklistService.java:90` | `ids` |
| B22 | `tag/TagService.java:41` | `tagIds` |
| B23 | `tag/TagService.java:116` | `ids` (`findTagId`) |

## 3. 서버에서 표시하는 방법 (사용자)

merge 뒤 `develop` 분석이 끝나면:

1. **오탐 10개**: 프로젝트 `one-blog` → **Issues** → 규칙 `java:S2259`로 거른다 → 위 표의 항목을 열고 상태를 **False positive**로 바꾼다. 댓글에 "queryForList는 빈 목록을 돌려줌 (030 hotspots.md)"을 적는다.
2. **핫스팟 28개**: **Security Hotspots** 탭 → 항목마다 **Review**에서 **Safe**를 고르고 위 근거 한 줄을 적는다. 정규식 3개(H29~H31)는 코드가 바뀌어 자동으로 닫힌다(남아 있으면 **Fixed**).
3. 이슈 상태를 바꿀 권한이 없으면 서버 관리자에게 프로젝트의 "Administer Issues"·"Administer Security Hotspots" 권한을 요청한다.

표시를 마치면 보안 등급(핫스팟 검토 100%)과 신뢰성 등급이 A로 보여야 한다(SC-001·SC-002).
