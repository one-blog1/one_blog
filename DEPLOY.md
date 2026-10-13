# 배포 안내

요구사항(4장 무중단 배포, 8장 D-117 등)은 문서 저장소 [one-blog1/one-blog_docs](https://github.com/one-blog1/one-blog_docs)에 있습니다.

배포 방식은 **GitHub Actions → Docker 이미지 → SSH로 서버에 실행**입니다 (D-118). **버전을 올렸을 때만 배포합니다** (D-120).

## 0. 자동 배포

```
build.gradle.kts의 version을 올려(배포마다 0.01씩, 예: 0.21 → 0.22. 규칙은 문서 저장소 releases/README.md, D-121) main에 push / PR merge
  → CI (.github/workflows/ci.yml): 테스트, jar 실행 확인, Docker 실행 확인
  → CI가 성공하면 Deploy (.github/workflows/deploy.yml)
      0) 버전 확인: 태그 v0.21이 아직 없을 때만 배포 (버전이 그대로면 CI만 돌고 끝)
      1) Docker 이미지 만들기 (태그 = 버전-커밋 앞 7자리)
      2) 이미지 + 프록시(nginx) 이미지 + scripts/deploy.sh + deploy.env(Secrets로 만듦)를 SSH로 서버 ~/one-blog/ 에 올림
      3) 서버에서 scripts/deploy.sh 실행 (무중단, D-135)
           쉬는 쪽(블루 8431 / 그린 8432)에 새 컨테이너 실행 → /api/health 확인
           → 8430 프록시가 새 포트로 보내도록 전환(nginx reload) → 옛 컨테이너 정상 종료(30초까지)
           새 버전이 상태 확인에 실패하면 새 것만 치우고 Actions를 실패로 표시 (지금 버전은 그대로 요청을 받음)
      4) 성공하면 그 커밋에 태그 v0.21을 붙임
```

**브랜치** (D-136): `main`(운영, 배포는 여기서만) / `develop`(다음 버전에 넣을 것을 모음) / `feature/*`(기능, develop에서 만들고 develop에 합친 뒤 지움) / `hotfix/*`(운영 버그, main에서 만들고 main·develop 양쪽에 합친 뒤 지움). CI는 네 종류 모두에서 돌고, 배포는 main만 합니다. 버전은 develop에서 올리고 develop을 main에 합칩니다.

**버전 올리는 법**: `build.gradle.kts`의 `version = "..."` 한 줄만 바꿉니다. 번호 규칙과 릴리즈 노트·공지 순서는 문서 저장소 [releases/README.md](https://github.com/one-blog1/one-blog_docs/blob/main/releases/README.md)를 따릅니다.

**지금 떠 있는 버전**: `curl http://<서버>:8430/api/health` → `{"status":"UP","version":"0.21"}`

**다시 배포**(시크릿을 바꿨을 때 등): Actions → Deploy → Run workflow. 버전과 상관없이 지금 main을 다시 배포합니다.


CI가 실패하면 배포하지 않습니다. 수동 배포는 Actions 탭의 Deploy에서 "Run workflow"를 누릅니다.

**GitHub Secrets** (Settings → Secrets and variables → Actions)

| 이름 | 필수 | 내용 |
|---|---|---|
| `SSH_ADDRESS`, `SSH_PORT`, `SSH_ID`, `SSH_PASSWORD` | 필수 | 서버 접속 정보 |
| `DB_ADDRESS`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | 필수 | 운영 DB. `DB_ADDRESS`는 호스트만 적습니다(포트는 `DB_PORT`) |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | 선택 | Gmail과 앱 비밀번호. 없으면 인증번호가 메일 대신 서버 로그에 찍힘 (`docker logs one-blog-blue` 또는 `one-blog-green`) |
| `ADMIN_LOGIN_ID`, `ADMIN_PASSWORD` | 선택 | 처음 시작할 때 관리자 계정을 만듦 |
| `COOKIE_SECURE` | 선택 | HTTPS를 붙인 뒤 `true`. 없으면 `false`(http에서도 로그인되도록) |
| `PUBLIC_BASE_URL` | 선택 | SNS 미리보기·소셜 로그인 돌아오는 주소에 쓸 사이트 주소. 없으면 `https://one-blog.java21.net` |
| `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET` | 선택 | 카카오 로그인 (D-126). REST API 키와 Client Secret. 시크릿을 끈 앱이면 ID만 |
| `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` | 선택 | 네이버 로그인 (D-126) |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | 선택 | 구글 로그인 (D-126) |
| `CODE_PEPPER`, `PRIVACY_HASH_PEPPER`, `JWT_SECRET` | 선택 | 넣으면 서버 `secrets.env` 대신 이 값을 씀. 로컬과 같은 DB를 쓰는 동안 `CODE_PEPPER`(`.env`에 있으면 `PRIVACY_HASH_PEPPER`도)는 로컬 `.env`와 같은 값이어야 함: 블랙리스트(이름·이메일·전화번호)를 이 값으로 해시해 DB에 저장하므로, 다르면 로컬에서 등록한 블랙리스트를 서버가 알아보지 못함 |

`JWT_SECRET`, `CODE_PEPPER`를 Secrets에 넣지 않으면 처음 배포할 때 서버가 만들어 `~/one-blog/secrets.env`에 두고 이후 계속 같은 값을 씁니다. 이 파일을 지우면 모든 로그인이 풀리고, 이메일 인증 중이던 코드와 블랙리스트 확인이 맞지 않게 됩니다.

**서버에 필요한 것**: Docker, curl, x86_64 CPU. SSH 계정이 `docker` 명령을 쓸 수 있어야 합니다 (`sudo usermod -aG docker <계정>` 후 다시 접속, sudo는 필요 없음). 이 앱은 8430~8432를 씁니다(배정받은 8430~8439 안). 바깥 nginx는 8430으로만 보냅니다.

**서버에서 볼 때**

```bash
docker ps                          # one-blog-proxy(8430)와 one-blog-blue(8431) 또는 one-blog-green(8432)
cat ~/one-blog/proxy/upstream.conf # 프록시가 지금 보내는 포트
docker logs -f one-blog-blue       # 서버 로그 (지금 켜진 색으로)
ls ~/one-blog                      # deploy.sh, deploy.env, secrets.env, proxy/
```

**무중단 배포 구조** (D-135)

```
바깥 nginx(도메인) → :8430 one-blog-proxy (nginx 컨테이너, 늘 켜져 있음)
                         → :8431 one-blog-blue  또는  :8432 one-blog-green (지금 켜진 쪽)
```

- 배포 때마다 쉬는 색에 새 버전을 띄우고, 상태 확인을 통과하면 `~/one-blog/proxy/upstream.conf`를 새 포트로 바꾸고 `nginx -s reload`로 다시 읽습니다. 끊기는 요청이 없습니다.
- 새 버전이 실패하면 지금 버전은 건드리지 않습니다. 실패한 컨테이너는 `one-blog-<색>-failed`로 남아 `docker logs`로 볼 수 있고, 다음 배포 때 지워집니다.
- 처음 한 번(예전 단일 컨테이너 `one-blog`가 8430을 쓰던 때)만 예전 컨테이너를 내리고 프록시를 띄우는 몇 초 동안 멈춥니다.
- 이전 버전으로 되돌리려면 이전 커밋을 다시 배포합니다: `gh workflow run Deploy --ref <태그 또는 커밋>` (그 커밋의 deploy.yml로 돎).
- 손으로 되돌릴 때(서버에서): 옛 이미지(`docker images one-blog`)로 쉬는 색 포트에 컨테이너를 띄우고 `upstream.conf`를 그 포트로 바꾼 뒤 `docker exec one-blog-proxy nginx -s reload`.
- 프록시는 바깥 nginx가 보낸 `X-Forwarded-For`·`X-Forwarded-Proto`를 그대로 넘깁니다. 앱은 `FORWARD_HEADERS_STRATEGY=none`이라 접속 IP를 프록시 주소(127.0.0.1)로 봅니다(예전에는 바깥 nginx 주소).

업로드 파일은 Docker 볼륨 `one-blog-uploads`에 있어 새 버전을 배포해도 남고, 블루·그린이 같이 씁니다. 컨테이너는 호스트 네트워크로 띄워 서버와 같은 네트워크·DNS를 씁니다(서버에서 `nc -vz <DB 주소> <포트>`가 되면 앱도 DB에 닿음).

## 1. 버전

### 서버에 직접 설치해야 하는 것

| 무엇 | 버전 | 비고 |
|---|---|---|
| Java (JDK 또는 JRE) | **21** | 실행만 하면 JRE 21로 충분합니다. 17이나 25로는 돌리지 않습니다. 추천 배포판은 Eclipse Temurin 21입니다. Docker로 배포하면 이미지 안에 들어 있어 따로 설치하지 않습니다 |
| MySQL | **8.4** | 문자셋 `utf8mb4`, 정렬 `utf8mb4_0900_ai_ci`입니다. 테이블은 앱이 시작할 때 Flyway가 만듭니다 |

### 자동으로 받는 것 (설치하지 않음)

| 무엇 | 버전 | 정해 둔 곳 |
|---|---|---|
| Gradle | 8.14.3 | `gradle/wrapper/gradle-wrapper.properties`, `./gradlew`가 받습니다 |
| Spring Boot | 4.1.1 | `build.gradle.kts` |
| Spring Security | 7.x | Spring Boot가 맞춰 줍니다 |
| Hibernate (JPA) | 7.x | Spring Boot가 맞춰 줍니다 |
| Flyway (+ flyway-mysql) | Spring Boot가 맞춤 | 마이그레이션 V1~V17은 `src/main/resources/db/migration`에 있습니다 |
| MySQL Connector/J | Spring Boot가 맞춤 | |
| commonmark (마크다운) | 0.24.0 | `build.gradle.kts` |
| jsoup (HTML 걸러내기) | 1.18.1 | `build.gradle.kts` |

### 화면에서 불러오는 것

| 무엇 | 버전 | 어디서 |
|---|---|---|
| Toast UI Editor | 3.2.2 | `uicdn.toast.com` CDN (글쓰기 화면) |
| Cloudflare Turnstile | v0 | `challenges.cloudflare.com` (키를 넣었을 때만) |
| 글꼴 IBM Plex Sans KR, Noto Serif KR | | `static/fonts`에 들어 있습니다 (외부에서 받지 않음) |

## 2. 만들기와 실행

```bash
./gradlew bootJar                     # → build/libs/one-blog.jar (실행 파일은 이것 하나)
java -jar build/libs/one-blog.jar     # 환경변수는 아래 3장
```

- 포트는 기본 8080입니다. 바꾸려면 `PORT`나 `SERVER_PORT`를 줍니다. Elastic Beanstalk는 앞단 Nginx가 5000번으로 보내므로 `SERVER_PORT=5000`을 줍니다.
- 앱은 실행한 폴더의 `.env`를 읽습니다. 서버에서는 `.env` 파일 대신 배포 환경의 환경변수 설정에 넣는 것을 권합니다.
- Docker로 배포할 때는 `docker build -t one-blog .`로 이미지를 만듭니다. 사용법은 `Dockerfile` 맨 위에 적어 두었습니다.
- 종료 신호를 받으면 처리 중인 요청을 최대 20초 기다린 뒤 꺼집니다 (graceful shutdown).

## 3. 환경변수

`.env.example`에 모든 값의 설명이 있습니다. 운영에서 꼭 볼 값만 모았습니다.

| 이름 | 운영 값 | 없으면 |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | 운영 DB 주소 | localhost:3306/one_blog |
| `DB_USERNAME`, `DB_PASSWORD` | 운영 DB 계정 | **시작 실패** |
| `JWT_SECRET` | 32바이트 이상 무작위 (`openssl rand -base64 48`) | **시작 실패** |
| `CODE_PEPPER` | 무작위 문자열 | **시작 실패** |
| `PRIVACY_HASH_PEPPER` | 무작위 문자열. 한 번 정하면 바꾸지 않습니다 | CODE_PEPPER를 씀 |
| `COOKIE_SECURE` | `true` (HTTPS) | true |
| `MAIL_MODE` | `smtp` | smtp |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Gmail 주소, 앱 비밀번호 16자리 | 인증 메일 발송 실패 |
| `PUBLIC_BASE_URL` | `https://실제-도메인` | 요청의 Host 헤더를 씀 |
| `FORWARD_HEADERS_STRATEGY` | 로드밸런서나 Nginx 뒤에 두면 `framework` | none |
| `FILE_STORAGE_DIR` | 서버를 바꿔도 남는 폴더 (EBS, 볼륨) | ./uploads |
| `VIDEO_THUMBNAIL_ENABLED` | 보통 그대로. 서버가 바깥(유튜브·치지직·SOOP)으로 요청을 못 보내면 `false` | true (글 첫 영상 링크의 목록 썸네일을 받아 둠, D-128) |
| `ADMIN_LOGIN_ID`, `ADMIN_PASSWORD` | 처음 한 번 관리자 계정을 만들 때만 | 관리자를 만들지 않음 |
| `TURNSTILE_SITE_KEY`, `TURNSTILE_SECRET_KEY` | Cloudflare에서 받은 키 | 사람 확인 꺼짐 |
| `KAKAO_CLIENT_ID`·`NAVER_CLIENT_ID`·`GOOGLE_CLIENT_ID`와 각 `_CLIENT_SECRET` | 각 사이트 개발자 화면에서 받은 키. 돌아오는 주소는 `{PUBLIC_BASE_URL}/login/oauth2/code/{kakao\|naver\|google}` | 그 사이트 로그인 단추가 안 보임 |

## 4. 배포 전 확인

- [ ] GitHub Actions의 **CI가 초록불**입니다 (테스트, jar 실행, Docker 실행을 모두 통과).
- [ ] **HTTPS로 접속합니다.** `COOKIE_SECURE=true`이면 http에서는 로그인 쿠키가 저장되지 않아 로그인이 안 됩니다. 인증서 없이 잠깐 http로 띄워 볼 때만 `false`로 둡니다.
- [ ] 로드밸런서나 Nginx 뒤에 두면 `FORWARD_HEADERS_STRATEGY=framework`로 둡니다. 두지 않으면 모든 요청이 같은 IP로 보여 요청 제한에 걸립니다. 직접 받을 때는 켜지 않습니다 (IP를 속일 수 있음).
- [ ] DB 계정에 테이블을 만들고 바꾸는 권한(CREATE, ALTER, INDEX, REFERENCES, DROP)이 있습니다. Flyway가 시작할 때 씁니다.
- [ ] 업로드 폴더(`FILE_STORAGE_DIR`)가 배포 때 지워지지 않는 곳입니다.
- [ ] 메일: `MAIL_MODE=smtp`와 Gmail 앱 비밀번호를 넣었습니다. `log`로 두면 가입 인증번호가 서버 로그에만 찍힙니다.
- [ ] 비밀값은 저장소, 이미지, 로그에 없습니다 (constitution III).

시간대는 따로 설정하지 않아도 됩니다. 서버가 어디서 돌든 앱이 한국 시간(Asia/Seoul)으로 고정하고, DB 연결의 시간대도 +09:00으로 맞춥니다 (D-117).

## 5. 배포 후 확인

```bash
./scripts/smoke-check.sh https://실제-도메인
```

`/api/health`가 200이 될 때까지 최대 2분 기다린 뒤 첫 화면, CSS·JS, 비회원 API, 로그인이 필요한 API가 예상한 대로 답하는지 봅니다. 로드밸런서의 상태 검사 주소도 `/api/health`로 둡니다. 정상이면 200, DB에 연결하지 못하면 503을 줍니다.

## 6. 업데이트할 때 지킬 것

배포한 뒤 기능을 고칠 때 서버가 시작하지 못하는 일을 막는 규칙입니다.

1. **이미 적용된 마이그레이션 파일(V1~V17)은 고치지 않습니다.** 한 글자만 바뀌어도 Flyway 검사합(checksum)이 달라져 서버가 시작하지 않습니다. 바꿀 내용은 새 파일 `V18__...sql`로 더합니다. ERD를 먼저 고칩니다 (constitution IV).
2. **DB 변경은 옛 버전과 새 버전이 함께 돌 수 있게 합니다** (4장 무중단 배포). 컬럼을 지우거나 이름을 바꿀 때는 "새 컬럼 추가 → 코드 전환 → 다음 배포에서 옛 컬럼 삭제"로 나눕니다.
3. **엔티티와 테이블을 같이 바꿉니다.** `ddl-auto: validate`라서 엔티티에 있는 컬럼이 테이블에 없으면 시작하지 않습니다.
4. **main에 올리기 전에 CI가 초록불인지 봅니다.** 빨간불이면 배포하지 않습니다.
5. **새 환경변수는 기본값을 둡니다** (`${이름:기본값}`). 기본값이 없으면 배포 환경에 넣는 것을 잊었을 때 시작하지 않습니다. 새 값은 `.env.example`과 이 문서 3장에 적습니다.
6. **새 화면(HTML)은 `SecurityConfig`의 허용 목록에 더합니다.** 빠지면 그 화면이 403이 됩니다.
