package com.sports.tracking.domain;

import java.util.Optional;

/**
 * Outbound port: a source of live scores for an event (e.g. an external scores
 * provider). The application depends on this abstraction; infrastructure
 * supplies the concrete adapter.
 */
public interface ScoreFeed {

    /**
     * Fetch the current score for an event.
     *
     * @return the score, or empty if the feed has no score for the event
     * @throws RuntimeException if the underlying source fails (callers decide
     *         how to handle transport errors)
     */
    Optional<Score> currentScore(String eventId);
}
