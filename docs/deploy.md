# 배포 안내

배포 방식(Elastic Beanstalk, EC2, Docker 등)은 아직 정하지 않았습니다. 이 문서는 어느 방식이든 똑같이 필요한 것만 적습니다. 배포 방식이 정해지면 그 방식의 절차를 아래에 더합니다.

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
| 글꼴 IBM Plex Sans KR, Jua | | `static/fonts`에 들어 있습니다 (외부에서 받지 않음) |

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
| `ADMIN_LOGIN_ID`, `ADMIN_PASSWORD` | 처음 한 번 관리자 계정을 만들 때만 | 관리자를 만들지 않음 |
| `TURNSTILE_SITE_KEY`, `TURNSTILE_SECRET_KEY` | Cloudflare에서 받은 키 | 사람 확인 꺼짐 |

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
