# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Build stage — compile the application and produce the executable (fat) jar.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy the Gradle wrapper and build configuration first. Resolving dependencies
# is then cached as its own layer and only re-runs when these files change,
# not on every source edit.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts lombok.config ./
# settings.gradle.kts includes the :component-tests subproject, so its build
# script must be present for Gradle to configure the build (its sources are not
# compiled by the bootJar task below).
COPY component-tests/build.gradle.kts ./component-tests/build.gradle.kts
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Copy the sources and build the boot jar. Tests are skipped in the image build;
# they run separately (and against real infrastructure) outside of it.
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---------------------------------------------------------------------------
# Runtime stage — a slim JRE that runs the jar as a non-root user.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# curl is used by the compose healthcheck to probe the actuator endpoint.
# The base image already ships a non-root user `ubuntu` (uid 1000), which we run as.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar
RUN chown ubuntu:ubuntu app.jar
USER ubuntu

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
