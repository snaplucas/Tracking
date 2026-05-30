package com.sports.tracking.infrastructure.messaging;

import com.sports.tracking.domain.Score;
import com.sports.tracking.domain.ScorePublisher;
import com.sports.tracking.domain.ScoreUpdate;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the retry/recover behaviour of {@link KafkaScorePublisher} under
 * failure conditions, with the real Spring {@code @EnableRetry} machinery in
 * play: transient failures are retried up to the configured attempts, and once
 * those are exhausted the failure is recovered (logged and swallowed) so the
 * caller's polling schedule is never killed.
 */
@SpringJUnitConfig
@TestPropertySource(properties = {
        "tracking.kafka.topic=test.scores",
        "tracking.kafka.publish.send-timeout-ms=1000",
        "tracking.kafka.publish.max-attempts=3",
        "tracking.kafka.publish.backoff-ms=1",
        "tracking.kafka.publish.backoff-multiplier=1.0"
})
class KafkaScorePublisherRetryTest {

    // proxyTargetClass = true mirrors Spring Boot's default (CGLIB) proxying, so
    // this test exercises the same proxy mode as the running application.
    @Configuration
    @EnableRetry(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, ScoreMessage> kafkaTemplate() {
            return mock(KafkaTemplate.class);
        }

        @Bean
        KafkaScorePublisher publisher(KafkaTemplate<String, ScoreMessage> kafkaTemplate,
                                      @Value("${tracking.kafka.topic}") String topic,
                                      @Value("${tracking.kafka.publish.send-timeout-ms}") long sendTimeoutMs) {
            return new KafkaScorePublisher(kafkaTemplate, topic, sendTimeoutMs);
        }
    }

    // Injected by interface: @EnableRetry wraps the bean in a JDK proxy of the
    // ScorePublisher interface, so it is not assignable to the concrete type.
    @Autowired
    private ScorePublisher publisher;

    @Autowired
    private KafkaTemplate<String, ScoreMessage> kafkaTemplate;

    private final ScoreUpdate update = ScoreUpdate.live("1234", Score.of("0:0"), Instant.now());

    @BeforeEach
    void resetSharedMock() {
        // The context (and its mock) is cached across tests; reset so per-test
        // stubbing and invocation counts start clean.
        reset(kafkaTemplate);
    }

    @Test
    void retriesTransientFailuresThenSucceeds() {
        SendResult<String, ScoreMessage> result = successResult();
        when(kafkaTemplate.send(anyString(), anyString(), any(ScoreMessage.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("down")))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("down")))
                .thenReturn(CompletableFuture.completedFuture(result));

        assertThatCode(() -> publisher.publish(update)).doesNotThrowAnyException();

        verify(kafkaTemplate, times(3)).send(anyString(), anyString(), any(ScoreMessage.class));
    }

    @Test
    void recoversAfterExhaustingRetries() {
        when(kafkaTemplate.send(anyString(), anyString(), any(ScoreMessage.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("down")));

        // recover() swallows the failure after the attempts are exhausted.
        assertThatCode(() -> publisher.publish(update)).doesNotThrowAnyException();

        verify(kafkaTemplate, times(3)).send(anyString(), anyString(), any(ScoreMessage.class));
    }

    @SuppressWarnings("unchecked")
    private SendResult<String, ScoreMessage> successResult() {
        RecordMetadata metadata = mock(RecordMetadata.class);
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(0L);
        SendResult<String, ScoreMessage> result = mock(SendResult.class);
        when(result.getRecordMetadata()).thenReturn(metadata);
        return result;
    }
}
