package com.sports.tracking.infrastructure.messaging;

import com.sports.tracking.domain.ScoreUpdate;
import com.sports.tracking.application.ScorePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Infrastructure adapter implementing the {@link ScorePublisher} port over
 * Kafka. Maps the {@link ScoreUpdate} domain event to a {@link ScoreMessage}
 * and publishes it keyed by event id, so all updates for one event land on the
 * same partition and stay ordered.
 *
 * <p>The send is awaited so transient failures surface as exceptions, which the
 * {@link Retryable} policy retries with exponential backoff. When the attempts
 * are exhausted, {@link #recover} logs and swallows the failure so a single bad
 * event never kills the recurring polling schedule.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaScorePublisher implements ScorePublisher {

    private final KafkaTemplate<String, ScoreMessage> kafkaTemplate;

    @Value("${tracking.kafka.topic}")
    private final String topic;

    /** How long to wait for the broker to acknowledge a single send attempt. */
    @Value("${tracking.kafka.publish.send-timeout-ms:5000}")
    private final long sendTimeoutMs;

    @Override
    @Retryable(
            retryFor = TransientPublishException.class,
            maxAttemptsExpression = "${tracking.kafka.publish.max-attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${tracking.kafka.publish.backoff-ms:500}",
                    multiplierExpression = "${tracking.kafka.publish.backoff-multiplier:2.0}"))
    public void publish(ScoreUpdate update) {
        ScoreMessage message = ScoreMessage.from(update);
        try {
            SendResult<String, ScoreMessage> result = kafkaTemplate
                    .send(topic, update.eventId(), message)
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            log.info("Published score for event {} to {}-{}@{}",
                    update.eventId(), topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TransientPublishException(
                    "Interrupted publishing score for event " + update.eventId(), ex);
        } catch (ExecutionException | TimeoutException ex) {
            log.warn("Transient failure publishing score for event {} to topic {} - will retry: {}",
                    update.eventId(), topic, ex.getMessage());
            throw new TransientPublishException(
                    "Failed to publish score for event " + update.eventId(), ex);
        }
    }

    /**
     * Last-resort handler once the retry attempts are exhausted: log the failure
     * and return normally so the caller's polling schedule keeps running.
     */
    @Recover
    @SuppressWarnings("unused")
    public void recover(TransientPublishException ex, ScoreUpdate update) {
        log.error("Giving up publishing score for event {} to topic {} after retries",
                update.eventId(), topic, ex);
    }
}
