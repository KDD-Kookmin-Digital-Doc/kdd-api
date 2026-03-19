FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY gradle ./gradle
COPY gradlew build.gradle settings.gradle ./
COPY src ./src
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre-jammy
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
RUN mkdir -p uploads && chown -R appuser:appgroup /app
USER appuser
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]
