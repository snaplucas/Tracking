package com.sports.tracking.domain;

/**
 * Outbound port: a destination for {@link ScoreUpdate} domain events (e.g. a
 * message broker). The application depends on this abstraction; infrastructure
 * supplies the concrete adapter.
 */
public interface ScorePublisher {

    void publish(ScoreUpdate update);
}
