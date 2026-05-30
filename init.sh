#!/usr/bin/env bash
#
# init.sh — bring up the infrastructure (Kafka) and start the tracking service.
#
# Usage:
#   ./init.sh          # start Kafka, build, and run the service (foreground)
#   ./init.sh down     # stop and remove the Kafka containers
#
set -euo pipefail

cd "$(dirname "$0")"

COMPOSE_FILE="docker-compose.yml"
KAFKA_CONTAINER="tracking-kafka"

# Pick `docker compose` (v2) or fall back to `docker-compose` (v1).
if docker compose version >/dev/null 2>&1; then
  DC="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  DC="docker-compose"
else
  echo "ERROR: docker compose is not installed or the Docker daemon is not running." >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Teardown mode
# ---------------------------------------------------------------------------
if [[ "${1:-}" == "down" ]]; then
  echo ">> Stopping containers..."
  $DC -f "$COMPOSE_FILE" down
  exit 0
fi

# ---------------------------------------------------------------------------
# 1. Start Kafka (and kafka-ui)
# ---------------------------------------------------------------------------
echo ">> Starting Kafka via Docker Compose..."
$DC -f "$COMPOSE_FILE" up -d

# ---------------------------------------------------------------------------
# 2. Wait for the broker to report healthy
# ---------------------------------------------------------------------------
echo ">> Waiting for Kafka to become healthy..."
for i in $(seq 1 40); do
  status=$(docker inspect -f '{{.State.Health.Status}}' "$KAFKA_CONTAINER" 2>/dev/null || echo "starting")
  if [[ "$status" == "healthy" ]]; then
    echo ">> Kafka is healthy."
    break
  fi
  if [[ "$i" == "40" ]]; then
    echo "ERROR: Kafka did not become healthy in time. Check: $DC logs kafka" >&2
    exit 1
  fi
  sleep 3
done

# ---------------------------------------------------------------------------
# 3. Build and run the Spring Boot service (foreground)
# ---------------------------------------------------------------------------
echo ">> Building and starting the tracking service on http://localhost:8080 ..."
echo ">> (Kafka UI available at http://localhost:8081)"
echo ">> Press Ctrl+C to stop the service. Run './init.sh down' to stop Kafka."

./gradlew bootRun
