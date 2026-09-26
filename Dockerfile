# Container image of the ASMS REST API. Build: docker build -t asms-backend . (or docker compose --profile app up --build)

# ===== Build stage =====
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Resolve dependencies first so this layer is cached until pom.xml changes
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q -DskipTests package \
    && cp target/*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ===== Runtime stage =====
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S asms && adduser -S asms -G asms
USER asms

ENV TZ=UTC \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# Least to most frequently changing layers for better image caching
COPY --from=build --chown=asms:asms /workspace/extracted/dependencies/ ./
COPY --from=build --chown=asms:asms /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=asms:asms /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=asms:asms /workspace/extracted/application/ ./

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
