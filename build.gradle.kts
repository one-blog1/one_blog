import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    // 테스트 커버리지 보고서 (028, D-140). Gradle에 들어 있는 플러그인이라 버전이 없다
    jacoco
    id("org.springframework.boot") version "4.1.1"
    // SonarQube 분석 (028, D-140). 실행에 Java 21, Gradle 8.4 이상이 필요하다. 올릴 때 SonarScanner for Gradle 문서에서 최신 버전 확인
    id("org.sonarqube") version "7.5.0.8588"
}

group = "com.oneblog"
version = "0.19"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot BOM으로 버전 관리 (dependency-management 플러그인 대신 Gradle platform 사용)
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    // JWT 인코딩·검증(Nimbus). Resource Server 설정은 Bearer 요청의 CSRF 검사를 끄므로 쓰지 않고 직접 필터를 둔다 (research R2)
    implementation("org.springframework.security:spring-security-oauth2-jose")
    // 소셜 로그인(카카오·네이버·구글, D-126). 스프링 부트 자동 설정 없이 라이브러리만 쓰고 SocialClientRegistrations에서 직접 설정한다
    implementation("org.springframework.security:spring-security-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-mysql")
    // 글 본문: 마크다운 → HTML (GFM 표·취소선), 서버에서 jsoup으로 위험한 태그 제거 (SEC-06, D-64)
    implementation("org.commonmark:commonmark:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.24.0")
    implementation("org.jsoup:jsoup:1.18.1")
    runtimeOnly("com.mysql:mysql-connector-j")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // E2E: 실제 브라우저(Chromium)로 화면을 눌러 보는 테스트 (028, D-140). 브라우저는 ./gradlew playwrightInstall 로 설치
    testImplementation("com.microsoft.playwright:playwright:1.63.0")
}

// 빌드 정보(버전·빌드 시각)를 META-INF/build-info.properties로 넣는다. /api/health가 지금 버전을 알려 줄 때 쓴다 (D-120)
// 버전을 올리려면 위의 version만 바꾼다. main에 올리면 그 버전으로 배포되고 태그 v버전이 붙는다 (DEPLOY.md)
springBoot {
    buildInfo()
}

// 배포용 jar는 bootJar 하나만 만든다 (build/libs에 실행 가능한 jar 하나: one-blog.jar)
tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName = "one-blog.jar"
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("spring.profiles.active", "test")
    // 운영 서버(main)와 같은 한국 시간으로 테스트한다 (D-117). CI 서버의 기본 시간대는 UTC
    systemProperty("user.timezone", "Asia/Seoul")
    // 실패한 테스트의 원인(예외)을 터미널에 바로 보여 준다
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = false
    }
    // 끝나면 "몇 개 실행, 몇 개 통과"를 한 줄 남긴다 (CI 로그에서 바로 보이게). 테스트를 하나도 못 찾으면 실패로 본다
    // (이름 규칙이 어긋나 E2E·단위 테스트가 통째로 빠져도 초록불이 되지 않게, 028)
    val taskName = name
    afterSuite(KotlinClosure2<TestDescriptor, TestResult, Unit>({ suite, result ->
        if (suite.parent == null) {
            val summary = "테스트 ${result.testCount}개: 통과 ${result.successfulTestCount}, 실패 ${result.failedTestCount}, 건너뜀 ${result.skippedTestCount}"
            println("[$taskName] $summary")
            // GitHub Actions에서는 실행 화면의 주석(Annotations)에도 남긴다
            if (System.getenv("GITHUB_ACTIONS") == "true") {
                println("::notice title=$taskName::$summary")
            }
            if (result.testCount == 0L) {
                throw GradleException("[$taskName] 실행된 테스트가 없습니다. 테스트 이름 규칙(...IntegrationTest, ...E2ETest)을 확인해 주세요")
            }
        }
    }))
}

// 테스트 이름 규칙 (028, D-140)
//   ...IntegrationTest = 서버·테스트 DB(MySQL)를 띄우는 통합 테스트
//   ...E2ETest         = 실제 브라우저로 화면을 누르는 E2E 테스트 (테스트 DB + Chromium 필요)
//   나머지 ...Test      = DB 없는 단위 테스트
// ./gradlew unitTest 단위만, DB 없이 빠르게 (CI 1단계 unit: 기능 브랜치 push와 PR)
// ./gradlew test     단위 + 통합 (E2E 제외. CI 2단계 full, 로컬 기본)
// ./gradlew e2eTest  E2E만 (CI 2단계 full에서 test 다음)
// DB가 필요한 테스트를 새로 만들면 이름을 ...IntegrationTest로 끝내야 1단계에서 빠진다
tasks.test {
    exclude("**/*E2ETest*")
}

val unitTest by tasks.registering(Test::class) {
    description = "DB 없이 도는 단위 테스트만 (*IntegrationTest, *E2ETest 제외)"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    exclude("**/*IntegrationTest*", "**/*E2ETest*")
}

val e2eTest by tasks.registering(Test::class) {
    description = "실제 브라우저로 핵심 흐름을 확인하는 E2E 테스트만 (*E2ETest). 테스트 DB와 Chromium(playwrightInstall)이 필요하다"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/*E2ETest*")
    // 실패 화면 캡처·trace를 남기는 폴더 (E2ETestSupport)
    systemProperty("e2e.artifacts", layout.buildDirectory.dir("e2e-artifacts").get().asFile.absolutePath)
}

// Playwright가 쓰는 Chromium과 OS 라이브러리를 설치한다. CI는 E2E 전에, 로컬은 처음 한 번
val playwrightInstall by tasks.registering(JavaExec::class) {
    description = "E2E용 Chromium 설치 (Playwright CLI)"
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "com.microsoft.playwright.CLI"
    args("install", "--with-deps", "chromium")
}

// 커버리지 보고서 (XML은 SonarQube가 읽고, HTML은 사람이 본다)
// build/reports/jacoco/test/            전체 테스트 기준 (SonarQube에 넘김)
// build/reports/jacoco/jacocoUnitTestReport/ 단위 테스트 기준 (CI 1단계에서 올림. 통합 테스트가 대부분이라 숫자가 낮다)
tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.test)
    // E2E를 돌렸으면 그 기록도 합친다 (화면에서만 쓰이는 코드 경로까지 커버리지에 들어가게)
    executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")) { include("test.exec", "e2eTest.exec") })
    reports {
        xml.required = true
        html.required = true
    }
}

val jacocoUnitTestReport by tasks.registering(JacocoReport::class) {
    description = "단위 테스트(unitTest) 커버리지 보고서"
    group = "verification"
    dependsOn(unitTest)
    executionData(layout.buildDirectory.file("jacoco/unitTest.exec"))
    sourceSets(sourceSets.main.get())
    reports {
        xml.required = true
        html.required = true
    }
}

// SonarQube 분석 (028, D-140). 서버 주소와 토큰은 여기 적지 않는다: 환경변수 SONAR_HOST_URL, SONAR_TOKEN (CI는 GitHub Secrets)
// 서버가 Community라 브랜치 속성(sonar.branch.name)은 넣지 않는다(넣으면 분석이 실패). develop·main 결과가 프로젝트 하나에 쌓인다
// sonar 작업은 테스트를 스스로 돌리지 않는다. 먼저 ./gradlew test e2eTest jacocoTestReport 를 돌린 뒤 ./gradlew sonar
sonar {
    properties {
        property("sonar.projectKey", "one-blog")
        property("sonar.projectName", "One Blog")
        // 서버의 "새 코드" 기준이 이전 버전이면, 새 코드 = 이번 버전 동안 쌓인 변경. 버전을 올리면 기준이 넘어간다
        property("sonar.projectVersion", version.toString())
        // 품질 게이트 결과를 기다렸다가 실패면 이 작업도 실패한다 → CI 실패 → main이면 배포 안 됨 (스펙 Q3)
        property("sonar.qualitygate.wait", "true")
        property("sonar.qualitygate.timeout", "300")
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml").get().asFile.absolutePath
        )
    }
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    // 저장소 루트의 .env를 읽을 수 있도록 작업 디렉터리를 프로젝트 루트로 둔다
    workingDir = rootDir
}
