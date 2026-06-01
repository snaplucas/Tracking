package com.sports.tracking.infrastructure.messaging;

import com.sports.tracking.domain.Score;
import com.sports.tracking.domain.ScoreUpdate;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the publish logic of {@link KafkaScorePublisher}: that a
 * {@link ScoreMessage} is built correctly from the domain event and sent keyed
 * by event id on success, and that a broker failure is surfaced as a
 * {@link TransientPublishException} (the signal the retry policy acts on).
 */
class KafkaScorePublisherTest {

    private static final String TOPIC = "test.scores";

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, ScoreMessage> kafkaTemplate = mock(KafkaTemplate.class);
    private final KafkaScorePublisher publisher = new KafkaScorePublisher(kafkaTemplate, TOPIC, 1000);

    private final ScoreUpdate update = ScoreUpdate.ofLive("1234", Score.of("2:1"), Instant.now());

    @Test
    void publishesMessageBuiltFromUpdateKeyedByEventId() {
        SendResult<String, ScoreMessage> result = successResult();
        when(kafkaTemplate.send(eq(TOPIC), eq("1234"), any(ScoreMessage.class)))
                .thenReturn(CompletableFuture.completedFuture(result));

        publisher.publish(update);

        ArgumentCaptor<ScoreMessage> captor = ArgumentCaptor.forClass(ScoreMessage.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq("1234"), captor.capture());
        ScoreMessage sent = captor.getValue();
        assertThat(sent.eventId()).isEqualTo("1234");
        assertThat(sent.score()).isEqualTo("2:1");
        assertThat(sent.status()).isEqualTo("LIVE");
        assertThat(sent.polledAt()).isNotNull();
    }

    @Test
    void wrapsBrokerFailureAsTransientPublishException() {
        when(kafkaTemplate.send(anyString(), anyString(), any(ScoreMessage.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThatThrownBy(() -> publisher.publish(update))
                .isInstanceOf(TransientPublishException.class)
                .hasMessageContaining("1234");
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
