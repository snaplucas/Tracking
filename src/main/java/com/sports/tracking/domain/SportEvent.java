package com.sports.tracking.domain;

import java.time.Instant;

public record SportEvent(String eventId, Score score, EventStatus status, Instant observedAt) {

    public SportEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
    }

    public static SportEvent ofLive(String eventId, Score score, Instant observedAt) {
        return new SportEvent(eventId, score, EventStatus.LIVE, observedAt);
    }
}
