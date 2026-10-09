# 빌드 단계: Docker 안에서 jar 를 만들어 서버에는 Docker 만 있으면 되게 한다
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY src/main src/main

# 테스트는 Testcontainers 라 Docker 빌드 안에서 돌 수 없다. 테스트는 이미지 밖에서 돌린다
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon \
    && mv build/libs/*.jar app.jar

# 실행 단계: JDK 없이 JRE 와 jar 만 담는다
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --uid 1001 app
COPY --from=build /workspace/app.jar app.jar
USER app

# 컨테이너 메모리 한도의 75% 까지만 힙으로 쓴다
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
