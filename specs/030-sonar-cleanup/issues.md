# 030 대상 이슈 목록 (SonarQube `one-blog`, 0.20 분석 기준)

줄 번호는 분석 시점 기준이라 029 merge 뒤 조금 다를 수 있다. 처리는 tasks에서 `수정`·`오탐`·`고치지 않음`으로 정한다.

## 버그·취약점 (34)

| # | 규칙 | 심각도 | 위치 | 내용 |
|---|---|---|---|---|
| B1 | java:S2229 | BLOCKER | `tag/TagService.java:64` | "findBlogTags's" @Transactional requirement is incompatible with the one for this method. |
| B2 | java:S6437 | BLOCKER | `auth/AuthService.java:59` | Revoke and change this password, as it is compromised. |
| B3 | java:S1751 | MAJOR | `post/PostPageRenderer.java:106` | Remove this "return" statement or make it conditional. |
| B4 | java:S1751 | MAJOR | `post/PostPageRenderer.java:109` | Remove this "return" statement or make it conditional. |
| B5 | java:S2259 | MAJOR | `account/PasswordResetService.java:177` | A "NullPointerException" could be thrown; "ids" is nullable here. |
| B6 | java:S2259 | MAJOR | `admin/AdminService.java:74` | A "NullPointerException" could be thrown; "queryForObject" is nullable here. |
| B7 | java:S2259 | MAJOR | `admin/AdminService.java:141` | A "NullPointerException" could be thrown; "queryForObject" is nullable here. |
| B8 | java:S2259 | MAJOR | `admin/AdminService.java:178` | A "NullPointerException" could be thrown; "queryForObject" is nullable here. |
| B9 | java:S2259 | MAJOR | `admin/AdminService.java:224` | A "NullPointerException" could be thrown; "queryForObject" is nullable here. |
| B10 | java:S2259 | MAJOR | `admin/AdminService.java:269` | A "NullPointerException" could be thrown; "queryForObject" is nullable here. |
| B11 | java:S2259 | MAJOR | `admin/AdminService.java:271` | A "NullPointerException" could be thrown; "queryForObject" is nullable here. |
| B12 | java:S2259 | MAJOR | `admin/AdminService.java:273` | A "NullPointerException" could be thrown; "queryForObject" is nullable here. |
| B13 | java:S2259 | MAJOR | `auth/AccessTokenService.java:93` | A "NullPointerException" could be thrown; "getExpiresAt()" can return null. |
| B14 | java:S2259 | MAJOR | `banner/BannerAdminService.java:219` | A "NullPointerException" could be thrown; "body" is nullable here. |
| B15 | java:S2259 | MAJOR | `banner/SiteSettings.java:22` | A "NullPointerException" could be thrown; "values" is nullable here. |
| B16 | java:S2259 | MAJOR | `batch/PurgeTask.java:89` | "NullPointerException" will be thrown when invoking method "purgePosts()". |
| B17 | java:S2259 | MAJOR | `batch/PurgeTask.java:120` | A "NullPointerException" could be thrown; "roots" is nullable here. |
| B18 | java:S2259 | MAJOR | `batch/PurgeTask.java:141` | A "NullPointerException" could be thrown; "ids" is nullable here. |
| B19 | java:S2259 | MAJOR | `blog/BlogAccessService.java:110` | A "NullPointerException" could be thrown; "reasons" is nullable here. |
| B20 | java:S2259 | MAJOR | `blog/BlogAccessService.java:120` | A "NullPointerException" could be thrown; "roles" is nullable here. |
| B21 | java:S2259 | MAJOR | `sanction/BlacklistService.java:90` | A "NullPointerException" could be thrown; "ids" is nullable here. |
| B22 | java:S2259 | MAJOR | `tag/TagService.java:41` | A "NullPointerException" could be thrown; "tagIds" is nullable here. |
| B23 | java:S2259 | MAJOR | `tag/TagService.java:116` | A "NullPointerException" could be thrown; "ids" is nullable here. |
| B24 | java:S2583 | MAJOR | `batch/CleanupTask.java:57` | Change this condition so that it does not always evaluate to "false" |
| B25 | java:S2583 | MAJOR | `batch/PurgeTask.java:82` | Change this condition so that it does not always evaluate to "false" |
| B26 | java:S2583 | MAJOR | `batch/SanctionCleanupTask.java:51` | Change this condition so that it does not always evaluate to "false" |
| B27 | java:S2583 | MAJOR | `file/FileController.java:72` | Change this condition so that it does not always evaluate to "false" |
| B28 | java:S2583 | MAJOR | `view/ViewCounter.java:44` | Change this condition so that it does not always evaluate to "false" |
| B29 | java:S3655 | MAJOR | `auth/social/SocialLoginHandlers.java:66` | Call "provider.isPresent()" or "!provider.isEmpty()" before accessing the value. |
| B30 | java:S5998 | MAJOR | `blog/BlogPolicy.java:21` | Refactor this repetition that can lead to a stack overflow for large inputs. |
| B31 | java:S6218 | MAJOR | `file/ImageMetadataStripper.java:194` | Override equals, hashCode and toString to consider array's content in the method |
| B32 | java:S6218 | MAJOR | `post/HttpVideoThumbnailFetcher.java:205` | Override equals, hashCode and toString to consider array's content in the method |
| B33 | java:S6218 | MAJOR | `post/VideoThumbnailService.java:122` | Override equals, hashCode and toString to consider array's content in the method |
| B34 | java:S6856 | MAJOR | `blog/BlogPageController.java:44` | Bind path variable "id" to a method parameter. |

## 코드 냄새 — B 범위 (23)

| # | 규칙 | 위치 | 내용 |
|---|---|---|---|
| S1 | java:S108 | `report/ReportService.java:253` | Either remove or fill this block of code. |
| S2 | java:S108 | `report/ReportService.java:293` | Either remove or fill this block of code. |
| S3 | java:S3776 | `account/AccountService.java:94` | Refactor this method to reduce its Cognitive Complexity from 17 to the 15 allowed. |
| S4 | java:S3776 | `auth/AuthService.java:111` | Refactor this method to reduce its Cognitive Complexity from 18 to the 15 allowed. |
| S5 | java:S3776 | `banner/BannerAdminService.java:188` | Refactor this method to reduce its Cognitive Complexity from 23 to the 15 allowed. |
| S6 | java:S3776 | `blog/join/BlogJoinService.java:74` | Refactor this method to reduce its Cognitive Complexity from 16 to the 15 allowed. |
| S7 | java:S3776 | `comment/CommentService.java:62` | Refactor this method to reduce its Cognitive Complexity from 20 to the 15 allowed. |
| S8 | java:S3776 | `common/text/MarkdownRenderer.java:104` | Refactor this method to reduce its Cognitive Complexity from 22 to the 15 allowed. |
| S9 | java:S3776 | `file/ImageMetadataStripper.java:42` | Refactor this method to reduce its Cognitive Complexity from 34 to the 15 allowed. |
| S10 | java:S3776 | `file/ImageMetadataStripper.java:256` | Refactor this method to reduce its Cognitive Complexity from 16 to the 15 allowed. |
| S11 | java:S3776 | `notification/NotificationQueryService.java:164` | Refactor this method to reduce its Cognitive Complexity from 30 to the 15 allowed. |
| S12 | java:S6541 | `file/ImageMetadataStripper.java:42` | A "Brain Method" was detected. Refactor it to reduce at least one of the following metrics: LOC from 67 to 64, Complexity from 21 to 14, Nesting Level from 4 to |
| S13 | java:S6809 | `account/AccountService.java:77` | Call transactional methods via an injected dependency instead of directly via 'this'. |
| S14 | java:S6809 | `account/AccountService.java:143` | Call transactional methods via an injected dependency instead of directly via 'this'. |
| S15 | java:S6809 | `account/WithdrawalService.java:80` | Call transactional methods via an injected dependency instead of directly via 'this'. |
| S16 | java:S6809 | `auth/AuthService.java:68` | Call transactional methods via an injected dependency instead of directly via 'this'. |
| S17 | java:S6809 | `blog/BlogAccessService.java:53` | Call transactional methods via an injected dependency instead of directly via 'this'. |
| S18 | java:S6809 | `blog/ops/BlogCloseService.java:54` | Call transactional methods via an injected dependency instead of directly via 'this'. |
| S19 | java:S6809 | `notification/NotificationQueryService.java:282` | Call transactional methods via an injected dependency instead of directly via 'this'. |
| S20 | java:S6809 | `post/PostCardService.java:48` | Call transactional methods via an injected dependency instead of directly via 'this'. |
| S21 | java:S6809 | `profile/PrivacyService.java:47` | Call transactional methods via an injected dependency instead of directly via 'this'. |
| S22 | java:S6809 | `sanction/SanctionService.java:70` | Call transactional methods via an injected dependency instead of directly via 'this'. |
| S23 | java:S6809 | `tag/TagService.java:64` | Call transactional methods via an injected dependency instead of directly via 'this'. |

## 보안 핫스팟 (31)

| # | 규칙 | 분류 | 위치 | 내용 |
|---|---|---|---|---|
| H1 | java:S2068 | auth | `mail/MailTexts.java:12` | 'PASSWORD' detected in this expression, review this potentially hard-coded password. |
| H2 | java:S2068 | auth | `member/SignupService.java:88` | 'password' detected in this expression, review this potentially hard-coded password. |
| H3 | java:S2068 | auth | `member/User.java:105` | 'PASSWORD' detected in this expression, review this potentially hard-coded password. |
| H4 | java:S2077 | sql-injection | `admin/AdminService.java:74` | Make sure using a dynamically formatted SQL query is safe here. |
| H5 | java:S2077 | sql-injection | `admin/AdminService.java:75` | Make sure using a dynamically formatted SQL query is safe here. |
| H6 | java:S2077 | sql-injection | `admin/AdminService.java:141` | Make sure using a dynamically formatted SQL query is safe here. |
| H7 | java:S2077 | sql-injection | `admin/AdminService.java:142` | Make sure using a dynamically formatted SQL query is safe here. |
| H8 | java:S2077 | sql-injection | `admin/AdminService.java:178` | Make sure using a dynamically formatted SQL query is safe here. |
| H9 | java:S2077 | sql-injection | `admin/AdminService.java:179` | Make sure using a dynamically formatted SQL query is safe here. |
| H10 | java:S2077 | sql-injection | `admin/AdminService.java:224` | Make sure using a dynamically formatted SQL query is safe here. |
| H11 | java:S2077 | sql-injection | `admin/AdminService.java:225` | Make sure using a dynamically formatted SQL query is safe here. |
| H12 | java:S2077 | sql-injection | `batch/PurgeTask.java:133` | Make sure using a dynamically formatted SQL query is safe here. |
| H13 | java:S2077 | sql-injection | `batch/PurgeTask.java:134` | Make sure using a dynamically formatted SQL query is safe here. |
| H14 | java:S2077 | sql-injection | `blog/ops/BlogMemberStatsService.java:86` | Make sure using a dynamically formatted SQL query is safe here. |
| H15 | java:S2077 | sql-injection | `blog/ops/BlogMemberStatsService.java:87` | Make sure using a dynamically formatted SQL query is safe here. |
| H16 | java:S2077 | sql-injection | `blog/ops/BlogMemberStatsService.java:101` | Make sure using a dynamically formatted SQL query is safe here. |
| H17 | java:S2077 | sql-injection | `blog/ops/BlogMemberStatsService.java:102` | Make sure using a dynamically formatted SQL query is safe here. |
| H18 | java:S2077 | sql-injection | `notification/NotificationQueryService.java:91` | Make sure using a dynamically formatted SQL query is safe here. |
| H19 | java:S2077 | sql-injection | `notification/NotificationQueryService.java:98` | Make sure using a dynamically formatted SQL query is safe here. |
| H20 | java:S2077 | sql-injection | `report/ReportService.java:349` | Make sure using a dynamically formatted SQL query is safe here. |
| H21 | java:S2077 | sql-injection | `report/ReportService.java:352` | Make sure using a dynamically formatted SQL query is safe here. |
| H22 | java:S2077 | sql-injection | `search/PagedIds.java:24` | Make sure using a dynamically formatted SQL query is safe here. |
| H23 | java:S2077 | sql-injection | `search/PagedIds.java:33` | Make sure using a dynamically formatted SQL query is safe here. |
| H24 | java:S2077 | sql-injection | `social/FollowService.java:105` | Make sure using a dynamically formatted SQL query is safe here. |
| H25 | java:S2077 | sql-injection | `social/FollowService.java:112` | Make sure using a dynamically formatted SQL query is safe here. |
| H26 | java:S2077 | sql-injection | `tag/TagPostService.java:63` | Make sure using a dynamically formatted SQL query is safe here. |
| H27 | java:S2077 | sql-injection | `tag/TagPostService.java:70` | Make sure using a dynamically formatted SQL query is safe here. |
| H28 | java:S2245 | weak-cryptography | `auth/social/SocialNicknames.java:77` | Make sure that using this pseudorandom number generator is safe here. |
| H29 | java:S5852 | dos | `auth/social/SocialClientRegistrations.java:35` | Make sure the regex used here, which is vulnerable to polynomial runtime due to backtracking, cannot lead to denial of service. |
| H30 | java:S5852 | dos | `common/text/MarkdownRenderer.java:128` | Make sure the regex used here, which is vulnerable to polynomial runtime due to backtracking, cannot lead to denial of service. |
| H31 | java:S5852 | dos | `post/PostPageRenderer.java:49` | Make sure the regex used here, which is vulnerable to polynomial runtime due to backtracking, cannot lead to denial of service. |
