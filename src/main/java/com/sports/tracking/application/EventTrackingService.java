package com.sports.tracking.application;

import com.sports.tracking.domain.SportEvent;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class EventTrackingService  implements TrackingService{

    private final TaskScheduler pollingTaskScheduler;
    private final ScoreClient scoreClient;
    private final ScorePublisher scorePublisher;

    @Value("${tracking.polling.interval-ms}")
    private final Duration pollInterval;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> liveTasks = new ConcurrentHashMap<>();

    @Override
    public boolean updateStatus(String eventId, boolean live) {
        if (live) {
            startTracking(eventId);
        } else {
            stopTracking(eventId);
        }
        return live;
    }

    private void startTracking(String eventId) {
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

     void pollOnce(String eventId) {
        try {
            scoreClient.getCurrentScore(eventId).ifPresentOrElse(
                    score -> {
                        SportEvent update = SportEvent.ofLive(eventId, score, Instant.now());
                        scorePublisher.publish(update);
                        log.info("Polled event {} -> score {}", eventId, score);
                    },
                    () -> log.warn("Score feed returned no score for event {} - skipping publish", eventId));
        } catch (Exception ex) {
            log.error("Error polling event {}: {}", eventId, ex.getMessage(), ex);
        }
    }

    @Override
    public Set<String> liveEvents() {
        return Set.copyOf(liveTasks.keySet());
    }

    @Override
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
