# 1단계: 빌드 스테이지
FROM gradle:8.5-jdk17 AS build
WORKDIR /app
COPY . .
# 테스트는 제외하고 빌드 (속도 향상 및 환경 의존성 제거)
RUN ./gradlew bootJar -x test

# 2단계: 실행 스테이지
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]