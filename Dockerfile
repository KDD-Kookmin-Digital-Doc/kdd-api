FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY gradle ./gradle
COPY gradlew build.gradle settings.gradle ./
COPY src ./src
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
# 호스트 bind mount(./uploads)의 소유자(Ubuntu 표준 UID 1000)와 정렬해 권한 충돌을 막는다.
# 정렬 안 하면 컨테이너 appuser가 호스트 디렉터리에 못 써서 chmod 777 우회가 필요해진다 (#47).
RUN addgroup -g 1000 -S appgroup && adduser -u 1000 -S appuser -G appgroup
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
RUN mkdir -p uploads && chown -R appuser:appgroup /app
USER appuser
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]
