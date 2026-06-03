# Live Sports Event Tracking Service

A Spring Boot microservice that tracks **live** sports events. For every event
marked *live*, it polls an external (mocked) scores REST API **every 10 seconds**,
transforms the response into a message, and publishes it to a **Kafka** topic.

Marking an event *not live* stops the polling for that event.

## Design decisions
* The project is using the Eric Evans ideas from domain-driven design
* The core is in the domain layer and the classes are not anemic (they can validate itself)
* Score is a value object for the SportEvent entity
* Event tracking service is orchestrating the use cases of tracking sports events
* The dependencies are inverted by using interfaces in the application layer, it makes it decoupled of any specific library or framework and also makes easy to adapt to different use cases
* EventTrackingService is implementing an interface , so in the most outside layer it is easy to change between different ways of tracking the events
* Everything is glued on AppConfig, which uses factory beans to instantiate the concrete classes
* For the layers I decided to go with a simpler and more straight forward approach with just 3 layers and the dependency rule instead of using a more traditional port and adapters approach
-- this sometimes can be unnecessary complicated and usually comes with an indirection overload
* When designing the classes and methods I tried to follow John Ousterhout's approach on modular design more than Uncle Bob's clean code
* The Docker part was done exclusively with AI
* Claude code was used to help to create the first working version of the project, and later I updated with the design approach mentioned previously 

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

### Architecture (hexagonal / clean architecture)

The code is organized into three layers. Dependencies point **inward**: the
domain knows nothing about Spring, HTTP, or Kafka; the application defines the
use-case and the ports it needs; infrastructure provides the adapters.

| Layer | Package | Contents |
|-------|---------|----------|
| **Domain** | `domain` | `Score`, `SportEvent`, `EventStatus` — pure value objects & the core domain event (no framework deps) |
| **Application** | `application` | `TrackingService` — inbound use-case port; `EventTrackingService` — its implementation (schedules/cancels per-event polling, builds `SportEvent`s); `ScoreClient` & `ScorePublisher` — outbound ports the core owns |
| **Infrastructure** | `infrastructure.entrypoint` | `EventStatusController` (inbound REST adapter), `MockExternalApiController`, `EventStatusDto` / `LiveEventsDto`, `ApiExceptionHandler` |
| | `infrastructure.communication` | `MockedScoreClient` implements `ScoreClient` over HTTP (`RestClient`); `ExternalEventDto` (anti-corruption wire DTO) |
| | `infrastructure.messaging` | `KafkaScorePublisher` implements `ScorePublisher` (with retry); `ScoreMessage` (Kafka JSON DTO); `TransientPublishException` |
| | `infrastructure.config` | `AppConfig` — `RestClient`, `TaskScheduler`, Kafka topic / producer / template beans |

> The external scores API is **mocked inside this same service**
> (`MockExternalApiController` at `/mock/external/...`) so the app runs
> end-to-end with no third party. `MockedScoreClient` still calls it over real
> HTTP; point `tracking.external.base-url` (env `EXTERNAL_API_BASE_URL`) at a
> real provider to use a real API.

---

## Prerequisites

- **Docker** + **Docker Compose** — runs Kafka, Kafka UI, **and the service itself**.
  This is all you need to run the app; the image is built inside Docker.
- **JDK 21** — only needed to run the tests / build locally outside Docker
  (the Gradle wrapper handles Gradle itself).

---

## Run it

```bash
./init.sh
```

`init.sh` will:

1. Start a dockerized Kafka broker (KRaft mode, no ZooKeeper) and Kafka UI via `docker-compose.yml`.
2. Wait for the broker to become healthy.
3. **Build the service image (`Dockerfile`) and run it as a container** (`tracking-app`)
   on **http://localhost:8080**, wired to Kafka over the in-network listener.

The service runs in the foreground so its logs stream to your terminal.

Useful URLs once running:

- Service:    http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI:    http://localhost:8080/v3/api-docs
- Health:     http://localhost:8080/actuator/health
- Kafka UI:   http://localhost:8081 (browse the `sports.events.scores` topic)

Stop the service with `Ctrl+C`. Stop and remove **all** containers (Kafka, UI, app) with:

```bash
./init.sh down
```

> The first run takes a few minutes: the Docker build downloads the Gradle
> distribution and all dependencies. Subsequent runs reuse the cached layers.

---

## REST API

The full, interactive contract is available in **Swagger UI** at
http://localhost:8080/swagger-ui/index.html. A quick tour with `curl`:

### Mark an event live / not live

```bash
# Start tracking event 1234 (polls every 10s, publishes to Kafka)
curl -X PUT http://localhost:8080/api/events/1234/status \
  -H 'Content-Type: application/json' \
  -d '{"live": true}'
# -> {"eventId":"1234","isLive":true}

# Stop tracking event 1234
curl -X PUT http://localhost:8080/api/events/1234/status \
  -H 'Content-Type: application/json' \
  -d '{"live": false}'
# -> {"eventId":"1234","isLive":false}
```

### Inspect what is being tracked

```bash
curl http://localhost:8080/api/events            # -> {"count":1,"liveEvents":["1234"]}
curl http://localhost:8080/api/events/1234       # -> {"eventId":"1234","isLive":true}
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
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker (compose sets this to `kafka:29092` for the app container) |
| `tracking.kafka.topic` | – | `sports.events.scores` | Destination topic |
| `tracking.external.base-url` | `EXTERNAL_API_BASE_URL` | `http://localhost:8080` | External scores API base URL (defaults to this service's own mock) |
| `tracking.polling.interval-ms` | – | `10000` | Poll interval per live event |
| `tracking.kafka.publish.send-timeout-ms` | – | `5000` | Per-attempt wait for a broker ack |
| `tracking.kafka.publish.max-attempts` | – | `3` | Publish retry attempts |
| `tracking.kafka.publish.backoff-ms` | – | `500` | Initial retry backoff |
| `tracking.kafka.publish.backoff-multiplier` | – | `2.0` | Exponential backoff multiplier |

---

## Error handling & logging

- Each polling tick is isolated: a failed external call is caught and logged in
  `EventTrackingService.pollOnce` and **does not** stop the recurring schedule
  for that event.
- Kafka publishing is **synchronous with retry**: `KafkaScorePublisher.publish`
  waits up to `send-timeout-ms` for an ack and, on a transient failure
  (`TransientPublishException`), retries with exponential backoff (Spring Retry
  `@Retryable`). Once attempts are exhausted, the `@Recover` method logs the
  failure and returns normally so polling keeps running.
- The producer is also configured with `acks=all` and `retries=3` at the
  Kafka-client level (`AppConfig`).
- Invalid request bodies return `400` with a JSON error; unexpected errors
  return `500` (`ApiExceptionHandler`).

---

## Tests

The build is split into two Gradle modules so the fast unit tests are isolated
from the slow, infrastructure-backed component tests. Tests use an **embedded
Kafka** broker, so Docker is not required to run them.

```bash
./gradlew test                    # run everything (both modules)
./gradlew :test                   # only the application module's unit tests (fast)
./gradlew :component-tests:test   # only the component tests (@SpringBootTest + embedded Kafka)
```

**Unit tests** — application module (`:`), fast:
- `domain.ScoreTest`, `domain.SportEventTest` — value-object construction, validation, equality.
- `application.EventTrackingServiceTest` — live/not-live scheduling, idempotency,
  the feed→`SportEvent` mapping, periodic polling, and error isolation. Drives the
  application service against mocked ports.
- `infrastructure.messaging.KafkaScorePublisherTest` — publish success path and
  message mapping.
- `infrastructure.messaging.KafkaScorePublisherRetryTest` — transient-failure
  retry and `@Recover` behaviour.
- `TrackingApplicationTests` — context-load smoke test with an embedded Kafka broker.

**Component tests** — `:component-tests` module (`@SpringBootTest`, full context):
- `component.LiveEventTrackingComponentTest` — boots the whole app on a real port
  with an **embedded Kafka** broker and exercises it end-to-end: REST `PUT .../status`
  → scheduled polling → real HTTP call to the mock API → transform → message consumed
  back off the Kafka topic. Also covers the REST contract (listing, status) and the
  `400` validation path. The module depends on the application via `project(":")`.

---

## Project layout (Gradle modules + layers)

```
tracking/                                     # root = the application module (:)
├── build.gradle.kts                          # Spring Boot app build (Boot 4.0, Java 21)
├── settings.gradle.kts                       # includes :component-tests
├── Dockerfile                                # multi-stage build -> runnable JRE image
├── docker-compose.yml                        # Kafka (KRaft) + Kafka UI + the app
├── init.sh                                   # start infra, build image, run the app
│
├── src/main/java/com/sports/tracking
│   ├── TrackingApplication.java              # Spring Boot entrypoint
│   │
│   ├── domain/                               # ── DOMAIN LAYER (no framework deps)
│   │   ├── Score.java                        #   value object
│   │   ├── SportEvent.java                   #   core domain event
│   │   └── EventStatus.java                  #   LIVE / NOT_LIVE
│   │
│   ├── application/                          # ── APPLICATION LAYER (use case + ports)
│   │   ├── TrackingService.java              #   inbound port (use case)
│   │   ├── EventTrackingService.java         #   impl: schedules polling, builds SportEvents
│   │   ├── ScoreClient.java                  #   outbound port (source of scores)
│   │   └── ScorePublisher.java               #   outbound port (sink for updates)
│   │
│   └── infrastructure/                       # ── INFRASTRUCTURE LAYER (adapters)
│       ├── entrypoint/                       #   inbound: REST controllers, DTOs, error handling
│       ├── communication/                    #   MockedScoreClient (impl ScoreClient) + wire DTO
│       ├── messaging/                        #   KafkaScorePublisher (impl ScorePublisher) + DTO
│       └── config/AppConfig.java             #   RestClient, TaskScheduler, Kafka beans
│
├── src/test/java/com/sports/tracking         # unit + context tests (domain, application, messaging)
│
└── component-tests/                          # ── :component-tests module
    ├── build.gradle.kts                      #   depends on project(":")
    └── src/test/java/.../component/          #   @SpringBootTest end-to-end test
```
