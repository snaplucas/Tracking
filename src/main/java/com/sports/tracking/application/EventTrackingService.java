package com.sports.tracking.application;

import com.sports.tracking.domain.ScoreUpdate;
import com.sports.tracking.domain.ScoreFeed;
import com.sports.tracking.domain.ScorePublisher;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Application service (use case orchestrator) for live-event tracking.
 *
 * <p>It owns the set of currently-live events and runs one recurring polling
 * task per live event. Each tick pulls the current score from the {@link
 * ScoreFeed} port, turns it into a {@link ScoreUpdate} domain event, and hands
 * it to the {@link ScorePublisher} port. It depends only on domain abstractions
 * — never on HTTP, Kafka, or any framework transport.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventTrackingService {

    private final TaskScheduler pollingTaskScheduler;
    private final ScoreFeed scoreFeed;
    private final ScorePublisher scorePublisher;

    /** Bare number is parsed by Spring as milliseconds (e.g. 10000 -> 10s). */
    @Value("${tracking.polling.interval-ms}")
    private final Duration pollInterval;

    /** eventId -> handle of its recurring polling task. */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> liveTasks = new ConcurrentHashMap<>();

    /**
     * Apply a live ↔ not-live status update for an event.
     *
     * @return {@code true} if the event is live after this call
     */
    public boolean updateStatus(String eventId, boolean live) {
        if (live) {
            startTracking(eventId);
        } else {
            stopTracking(eventId);
        }
        return live;
    }

    private void startTracking(String eventId) {
        // computeIfAbsent makes this idempotent: a repeated "live" update for an
        // already-tracked event does not spawn a second task.
        liveTasks.computeIfAbsent(eventId, id -> {
            log.info("Event {} marked LIVE - scheduling poll every {}", id, pollInterval);
            return pollingTaskScheduler.scheduleAtFixedRate(() -> pollOnce(id), pollInterval);
        });
    }

    private void stopTracking(String eventId) {
        ScheduledFuture<?> task = liveTasks.remove(eventId);
        if (task != null) {
            task.cancel(false);
            log.info("Event {} marked NOT LIVE - polling stopped", eventId);
        } else {
            log.info("Event {} marked NOT LIVE - was not being tracked", eventId);
        }
    }

    /** One polling tick: fetch -> build domain event -> publish, error-isolated. */
    public void pollOnce(String eventId) {
        try {
            scoreFeed.currentScore(eventId).ifPresentOrElse(
                    score -> {
                        ScoreUpdate update = ScoreUpdate.live(eventId, score, Instant.now());
                        scorePublisher.publish(update);
                        log.info("Polled event {} -> score {}", eventId, score);
                    },
                    () -> log.warn("Score feed returned no score for event {} - skipping publish", eventId));
        } catch (Exception ex) {
            // Never let one failed tick kill the recurring schedule.
            log.error("Error polling event {}: {}", eventId, ex.getMessage(), ex);
        }
    }

    /** Event ids currently being tracked as live. */
    public Set<String> liveEvents() {
        return Set.copyOf(liveTasks.keySet());
    }

    public boolean isLive(String eventId) {
        return liveTasks.containsKey(eventId);
    }

    @PreDestroy
    void shutdown() {
        log.info("Shutting down - cancelling {} live polling task(s)", liveTasks.size());
        liveTasks.values().forEach(task -> task.cancel(false));
        liveTasks.clear();
    }
}
