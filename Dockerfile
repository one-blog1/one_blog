# One Blog 서버 이미지 (D-117). 배포 방식이 Docker(ECS, EC2 + Docker 등)일 때 쓴다. jar로 바로 배포하면 필요 없다.
# 만들기: docker build -t one-blog .
# 실행:   docker run -p 8080:8080 --env-file .env -v one-blog-uploads:/app/uploads one-blog
# 비밀값은 이미지에 넣지 않는다(.dockerignore로 .env 제외). 실행할 때 환경변수로 준다 (constitution III).

# 1단계: 빌드 (JDK 21 + Gradle Wrapper)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
# 의존성만 먼저 받아 두면 소스만 바뀐 다시 빌드가 빨라진다
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar

# 2단계: 실행 (JRE 21만)
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 oneblog && mkdir -p /app/uploads && chown oneblog /app/uploads
COPY --from=build /src/build/libs/one-blog.jar app.jar
USER oneblog
ENV TZ=Asia/Seoul \
    FILE_STORAGE_DIR=/app/uploads \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# 업로드 파일은 컨테이너가 바뀌어도 남도록 볼륨으로 (D-75)
VOLUME /app/uploads
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
