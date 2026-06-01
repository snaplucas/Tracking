package com.sports.tracking.infrastructure.messaging;

import com.sports.tracking.domain.ScoreUpdate;

import java.time.Instant;

public record ScoreMessage(String eventId, String score, String status, Instant polledAt) {

    public static ScoreMessage from(ScoreUpdate update) {
        return new ScoreMessage(
                update.eventId(),
                update.score().value(),
                update.status().name(),
                update.observedAt());
    }
}
