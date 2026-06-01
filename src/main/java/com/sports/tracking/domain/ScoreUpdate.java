package com.sports.tracking.domain;

import java.time.Instant;

/**
 * Domain event produced every time a live event is observed: the score of an
 * event at a given instant. This is the core domain concept that the
 * application publishes; infrastructure decides how to serialize/transport it.
 *
 * @param eventId    identity of the observed event
 * @param score      score at observation time
 * @param status     status of the event when observed
 * @param observedAt instant the observation was made
 */
public record ScoreUpdate(String eventId, Score score, EventStatus status, Instant observedAt) {

    public ScoreUpdate {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
    }

    public static ScoreUpdate ofLive(String eventId, Score score, Instant observedAt) {
        return new ScoreUpdate(eventId, score, EventStatus.LIVE, observedAt);
    }
}
