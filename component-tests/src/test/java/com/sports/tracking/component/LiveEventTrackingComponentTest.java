package com.sports.tracking.component;

import com.sports.tracking.TrackingApplication;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Component test: boots the whole application on a real port with an embedded
 * Kafka broker and drives it through its real edges.
 *
 * <p>The flow exercised end-to-end is: REST {@code PUT .../status {live:true}} →
 * the application schedules polling → it calls the (in-app) mock external API
 * over real HTTP → transforms the response → publishes a {@code ScoreMessage}
 * to Kafka, which a real consumer reads back and asserts on.
 *
 * <p>This test lives in its own Gradle module ({@code :component-tests}) so the
 * slow, infrastructure-backed tests are isolated from the fast unit tests in the
 * application module. It depends on the application via {@code project(":")} and
 * boots it explicitly through {@link TrackingApplication}.
 *
 * <p>{@code server.port} and {@code tracking.external.base-url} are pinned to the
 * same fixed port so the scheduler's self-call reaches the mock controller, and
 * the poll interval is shortened so the test runs quickly.
 */
@SpringBootTest(
        classes = TrackingApplication.class,
        webEnvironment = WebEnvironment.DEFINED_PORT,
        properties = {
                "server.port=18085",
                "tracking.external.base-url=http://localhost:18085",
                "tracking.polling.interval-ms=300"
        })
@EmbeddedKafka(
        partitions = 1,
        topics = "sports.events.scores"
)
class LiveEventTrackingComponentTest {

    private static final String BASE_URL = "http://localhost:18085";

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Value("${tracking.kafka.topic}")
    private String topic;

    private final RestClient rest = RestClient.create(BASE_URL);
    private final ObjectMapper json = new ObjectMapper();

    // ------------------------------------------------------------------ //
    // End-to-end: live event -> Kafka                                    //
    // ------------------------------------------------------------------ //

    @Test
    void liveEventIsPolledAndScorePublishedToKafka() {
        String eventId = "evt-e2e";

        try (Consumer<String, String> consumer = newConsumer()) {
            ResponseEntity<String> response = putStatus(eventId, "{\"live\": true}");
            assertThat(response.getStatusCode().value()).isEqualTo(200);

            ConsumerRecord<String, String> record =
                    awaitRecordForKey(consumer, eventId, Duration.ofSeconds(15));

            assertThat(record).as("a score message keyed by %s", eventId).isNotNull();
            JsonNode message = json.readTree(record.value());
            assertThat(message.get("eventId").asString()).isEqualTo(eventId);
            assertThat(message.get("status").asString()).isEqualTo("LIVE");
            assertThat(message.get("score").asString()).matches("\\d+:\\d+");
            assertThat(message.get("polledAt").asString()).isNotBlank();
        } finally {
            putStatus(eventId, "{\"live\": false}");
        }
    }

    // ------------------------------------------------------------------ //
    // REST API contract                                                  //
    // ------------------------------------------------------------------ //

    @Test
    void marksEventLiveThenNotLiveAndReflectsItInListings() {
        String eventId = "evt-rest";
        try {
            ResponseEntity<String> live = putStatus(eventId, "{\"live\": true}");
            assertThat(live.getStatusCode().value()).isEqualTo(200);
            assertThat(json.readTree(live.getBody()).get("live").asBoolean()).isTrue();

            String list = rest.get().uri("/api/events").retrieve().body(String.class);
            assertThat(json.readTree(list).get("liveEvents").toString()).contains(eventId);

            String status = rest.get().uri("/api/events/{id}", eventId).retrieve().body(String.class);
            assertThat(json.readTree(status).get("live").asBoolean()).isTrue();
        } finally {
            ResponseEntity<String> notLive = putStatus(eventId, "{\"live\": false}");
            assertThat(notLive.getStatusCode().value()).isEqualTo(200);
            assertThat(json.readTree(notLive.getBody()).get("live").asBoolean()).isFalse();
        }
    }

    @Test
    void invalidStatusBodyReturns400() {
        ResponseEntity<String> response = putStatus("evt-bad", "{}"); // missing "live"

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json.readTree(response.getBody()).get("error").asString()).isEqualTo("validation_failed");
    }

    @Test
    void mockExternalApiReturnsContractShape() throws Exception {
        String body = rest.get().uri("/mock/external/events/{id}", "evt-mock").retrieve().body(String.class);

        JsonNode node = json.readTree(body);
        assertThat(node.get("eventId").asString()).isEqualTo("evt-mock");
        assertThat(node.get("currentScore").asString()).matches("\\d+:\\d+");
    }

    // ------------------------------------------------------------------ //
    // Helpers                                                            //
    // ------------------------------------------------------------------ //

    /** PUT a status update and capture status + body without throwing on 4xx. */
    private ResponseEntity<String> putStatus(String eventId, String jsonBody) {
        return rest.put()
                .uri("/api/events/{id}/status", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBody)
                .exchange((request, response) -> ResponseEntity
                        .status(response.getStatusCode())
                        .body(response.bodyTo(String.class)));
    }

    private Consumer<String, String> newConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "component-test");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, topic);
        return consumer;
    }

    private ConsumerRecord<String, String> awaitRecordForKey(Consumer<String, String> consumer,
                                                            String key, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
            for (ConsumerRecord<String, String> record : records) {
                if (key.equals(record.key())) {
                    return record;
                }
            }
        }
        return null;
    }
}
