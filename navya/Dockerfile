# syntax=docker/dockerfile:1

# ---- build stage: compile and package with Maven ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# copy the pom alone first so the dependency download is cached until the pom changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# tests run in CI before the image is built, so they are skipped here
RUN mvn -B -q package -DskipTests

# ---- run stage: JRE only, no build tools ----
FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 app && useradd --uid 10001 --gid 10001 --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir /data && chown 10001:10001 /data
WORKDIR /app
COPY --from=build /src/target/navya.jar /app/navya.jar
USER 10001:10001
ENV PORT=8080 \
    DATA_DIR=/data \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
# TCP check only: the base image has no curl. Cloud platforms probe GET /health over HTTP themselves.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${PORT} || exit 1"]
# secrets (JWT_SECRET, DB_PASSWORD) are passed as environment variables at run time, never baked in
ENTRYPOINT ["java", "-jar", "/app/navya.jar"]
