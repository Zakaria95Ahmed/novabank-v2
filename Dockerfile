# syntax=docker/dockerfile:1
# =====================================================================
# One Dockerfile for ALL NovaBank services (build context = repo root).
#
#   docker build -t novabank-api-gateway \
#     --build-arg SERVICE=api-gateway --build-arg PORT=8080 .
#
# SERVICE = folder name under services/
# =====================================================================

# ---------- Stage 1: Build ----------
FROM maven:3.9.12-amazoncorretto-21 AS build

ARG SERVICE
WORKDIR /build

# The whole reactor is needed (parent POM + contracts + the chosen service).
COPY . .

# -pl builds only the chosen module, -am also builds what it depends on.
# The cache mount keeps ~/.m2 between builds so dependencies are not re-downloaded.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl services/${SERVICE} -am clean package -DskipTests

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:24-jre AS runtime

ARG SERVICE
# Informational only; the real port mapping is done in docker-compose.
ARG PORT=8080
ENV PORT=${PORT}

# curl is used by compose healthchecks; run as a non-root user.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --no-create-home appuser

WORKDIR /app
COPY --from=build --chown=appuser /build/services/${SERVICE}/target/*.jar app.jar
USER appuser

EXPOSE ${PORT}
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# TODO (P1 / SEC-02): add a HEALTHCHECK on the internal management port.
# It is intentionally NOT here yet: in P0 the actuator is still behind Spring Security
# on the main port, so a health probe would report the service as unhealthy.

ENTRYPOINT ["java", "-jar", "app.jar"]
