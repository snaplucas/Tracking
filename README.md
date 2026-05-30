# Live Sports Event Tracking Service

A Spring Boot microservice that tracks **live** sports events. For every event
marked *live*, it polls an external (mocked) scores REST API **every 10 seconds**,
transforms the response into a message, and publishes it to a **Kafka** topic.

Marking an event *not live* stops the polling for that event.

---

## How it works

```
                PUT /api/events/{id}/status {"live": true}
                          │
                          ▼
   ┌─────────────────────────────────────────┐
   │            EventTrackingService          │
   │  keeps one scheduled task per live event │
   └─────────────────────────────────────────┘
                          │  every 10s
                          ▼
   GET /mock/external/events/{id}  ──►  { "eventId": "1234", "currentScore": "0:0" }
                          │  transform
                          ▼
   ScoreMessage { eventId, score, status:"LIVE", polledAt }
                          │  publish (keyed by eventId)
                          ▼
                 Kafka topic: sports.events.scores
```

### Architecture (Domain-Driven Design / hexagonal)

The code is organized into three layers. Dependencies point **inward**: the
domain knows nothing about Spring, HTTP, or Kafka; the application depends only
on domain ports; infrastructure provides the adapters.

| Layer | Package | Contents |
|-------|---------|----------|
| **Domain** | `domain.model` | `Score`, `ScoreUpdate`, `EventStatus` — value objects & the core domain event |
| | `domain.port` | `ScoreFeed`, `ScorePublisher` — outbound ports the core owns |
| **Application** | `application` | `EventTrackingService` — the use case: schedule/cancel per-event polling, build `ScoreUpdate`s, hand them to the ports |
| **Infrastructure** | `infrastructure.web` | `EventStatusController` (inbound adapter), `MockExternalApiController`, error handling |
| | `infrastructure.external` | `RestScoreFeed` implements `ScoreFeed` over HTTP; `ExternalEventResponse` (anti-corruption wire DTO) |
| | `infrastructure.messaging` | `KafkaScorePublisher` implements `ScorePublisher`; `ScoreMessage` (Kafka serialization DTO) |
| | `infrastructure.config` | `AppConfig` — `RestClient`, `TaskScheduler`, Kafka topic & producer beans |

> The external API is **mocked inside this same service** (`/mock/external/...`)
> so the app runs end-to-end with no third-party dependency. Point
> `tracking.external.base-url` at a real provider to use a real API.

---

## Prerequisites

- **Docker** + **Docker Compose** (for the Kafka broker)
- **JDK 21** (the Gradle wrapper handles Gradle itself)

---

## Run it

```bash
./init.sh
```

`init.sh` will:

1. Start a dockerized Kafka broker (KRaft mode, no ZooKeeper) and Kafka UI via `docker-compose.yml`.
2. Wait for the broker to become healthy.
3. Build and start the Spring Boot service (`./gradlew bootRun`) on **http://localhost:8080**.

Useful URLs once running:

- Service: http://localhost:8080
- Health:  http://localhost:8080/actuator/health
- Kafka UI: http://localhost:8081 (browse the `sports.events.scores` topic)

Stop the service with `Ctrl+C`. Stop and remove the Kafka containers with:

```bash
./init.sh down
```

---

## REST API

### Mark an event live / not live

```bash
# Start tracking event 1234 (polls every 10s, publishes to Kafka)
curl -X PUT http://localhost:8080/api/events/1234/status \
  -H 'Content-Type: application/json' \
  -d '{"live": true}'

# Stop tracking event 1234
curl -X PUT http://localhost:8080/api/events/1234/status \
  -H 'Content-Type: application/json' \
  -d '{"live": false}'
```

### Inspect what is being tracked

```bash
curl http://localhost:8080/api/events            # -> {"count":1,"liveEvents":["1234"]}
curl http://localhost:8080/api/events/1234       # -> {"eventId":"1234","live":true}
```

---

## Verify messages are published

**Option A — Kafka UI:** open http://localhost:8081 → cluster `local` →
topic `sports.events.scores` → *Messages*.

**Option B — console consumer** (inside the broker container):

```bash
docker exec -it tracking-kafka \
  /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic sports.events.scores \
  --from-beginning
```

You should see one JSON message roughly every 10 seconds per live event:

```json
{"eventId":"1234","score":"0:1","status":"LIVE","polledAt":"2026-05-30T12:00:10.123Z"}
```

---

## Configuration

All settings live in `src/main/resources/application.yml` and can be
overridden via environment variables:

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker |
| `tracking.kafka.topic` | – | `sports.events.scores` | Destination topic |
| `tracking.external.base-url` | `EXTERNAL_API_BASE_URL` | `http://localhost:8080` | External scores API base URL |
| `tracking.polling.interval-ms` | – | `10000` | Poll interval per live event |

---

## Error handling & logging

- Each polling tick is isolated: a failed external call or Kafka send is logged
  (`EventTrackingService` / `ScorePublisher`) and **does not** stop the recurring
  schedule for that event.
- Invalid request bodies return `400` with a JSON error (`ApiExceptionHandler`).
- Kafka sends are asynchronous; failures are logged via the send callback.

---

## Tests

The build is split into two Gradle modules so the fast unit tests are isolated
from the slow, infrastructure-backed component tests.

```bash
./gradlew test                    # run everything (both modules)
./gradlew :test                   # only the application module's unit tests (fast)
./gradlew :component-tests:test   # only the component tests (@SpringBootTest + embedded Kafka)
```

**Unit tests** — application module (`:`), fast, no infrastructure:
- `domain.ScoreTest`, `domain.ScoreUpdateTest` — value-object construction, validation, equality.
- `application.EventTrackingServiceTest` — live/not-live scheduling, idempotency,
  the feed→`ScoreUpdate` mapping, periodic polling, and error isolation. Drives the
  application service against mocked domain ports.
- `TrackingApplicationTests` — context load smoke test using an embedded Kafka broker.

**Component tests** — `:component-tests` module (`@SpringBootTest`, full context):
- `component.LiveEventTrackingComponentTest` — boots the whole app on a real port
  with an **embedded Kafka** broker and exercises it end-to-end: REST `PUT .../status`
  → scheduled polling → real HTTP call to the mock API → transform → message consumed
  back off the Kafka topic. Also covers the REST contract (listing, status) and the
  `400` validation path. The module depends on the application via `project(":")`.

---

## Project layout (Gradle modules + DDD layers)

```
tracking/                                     # root = the application module (:)
├── build.gradle.kts                          # Spring Boot app build
├── settings.gradle.kts                       # includes :component-tests
│
├── src/main/java/com/sports/tracking
│   ├── TrackingApplication.java              # Spring Boot entrypoint
│   │
│   ├── domain/                               # ── DOMAIN LAYER (no framework deps)
│   │   ├── Score.java                        #   value object
│   │   ├── ScoreUpdate.java                  #   core domain event
│   │   ├── EventStatus.java                  #   value object
│   │   ├── ScoreFeed.java                    #   outbound port (source of scores)
│   │   └── ScorePublisher.java               #   outbound port (sink for updates)
│   │
│   ├── application/                          # ── APPLICATION LAYER (use cases)
│   │   └── EventTrackingService.java         #   schedules polling, builds ScoreUpdates
│   │
│   └── infrastructure/                       # ── INFRASTRUCTURE LAYER (adapters)
│       ├── web/                              #   inbound: REST controllers + errors
│       ├── external/                         #   RestScoreFeed (impl ScoreFeed) + wire DTO
│       ├── messaging/                        #   KafkaScorePublisher (impl ScorePublisher) + DTO
│       └── config/AppConfig.java             #   RestClient, TaskScheduler, Kafka beans
│
├── src/test/java/com/sports/tracking         # unit + context tests (domain, application)
│
├── component-tests/                          # ── :component-tests module
│   ├── build.gradle.kts                      #   depends on project(":")
│   └── src/test/java/.../component/          #   @SpringBootTest end-to-end tests
│
├── docker-compose.yml                        # dockerized Kafka (KRaft) + Kafka UI
└── init.sh                                   # start infra + service
```
