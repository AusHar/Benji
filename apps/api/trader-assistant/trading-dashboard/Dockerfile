# syntax=docker/dockerfile:1

# Build stage
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace/app

COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY openAPI.yaml ./
COPY src ./src

RUN chmod +x gradlew
RUN ./gradlew --no-daemon clean bootJar

# Runtime stage
FROM eclipse-temurin:21-jre-jammy AS runner
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -XX:+UseG1GC -XX:+UseStringDeduplication -XX:MaxMetaspaceSize=256m -Djava.security.egd=file:/dev/./urandom"

COPY --from=builder /workspace/app/build/libs/*.jar /app/app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f -u "${MANAGEMENT_USERNAME:-actuator}:${MANAGEMENT_PASSWORD}" \
        http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
