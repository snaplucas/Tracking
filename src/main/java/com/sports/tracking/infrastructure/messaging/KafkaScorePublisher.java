package com.sports.tracking.infrastructure.messaging;

import com.sports.tracking.domain.ScoreUpdate;
import com.sports.tracking.domain.ScorePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter implementing the {@link ScorePublisher} port over
 * Kafka. Maps the {@link ScoreUpdate} domain event to a {@link ScoreMessage}
 * and publishes it keyed by event id, so all updates for one event land on the
 * same partition and stay ordered.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaScorePublisher implements ScorePublisher {

    private final KafkaTemplate<String, ScoreMessage> kafkaTemplate;

    @Value("${tracking.kafka.topic}")
    private final String topic;

    @Override
    public void publish(ScoreUpdate update) {
        ScoreMessage message = ScoreMessage.from(update);
        kafkaTemplate.send(topic, update.eventId(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish score for event {} to topic {}",
                                update.eventId(), topic, ex);
                    } else if (log.isDebugEnabled()) {
                        log.debug("Published score for event {} to {}-{}@{}",
                                update.eventId(), topic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
