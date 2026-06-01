package com.sports.tracking.infrastructure.messaging;

import com.sports.tracking.domain.SportEvent;
import lombok.Builder;

import java.time.Instant;

@Builder
public record ScoreMessage(String eventId, String score, String status, Instant polledAt) {

    public static ScoreMessage from(SportEvent update) {
        return ScoreMessage.builder()
                .eventId(update.eventId())
                .score(update.score().value())
                .status(update.status().name())
                .polledAt(update.observedAt())
                .build();
    }
}
