package com.sports.tracking.infrastructure.messaging;

import com.sports.tracking.domain.ScoreUpdate;

import java.time.Instant;

/**
 * Kafka serialization model for a {@link ScoreUpdate}. Kept in infrastructure so
 * the published JSON contract can evolve independently of the domain model.
 *
 * <pre>
 * { "eventId": "1234", "score": "0:1", "status": "LIVE", "polledAt": "..." }
 * </pre>
 */
public record ScoreMessage(String eventId, String score, String status, Instant polledAt) {

    public static ScoreMessage from(ScoreUpdate update) {
        return new ScoreMessage(
                update.eventId(),
                update.score().value(),
                update.status().name(),
                update.observedAt());
    }
}
