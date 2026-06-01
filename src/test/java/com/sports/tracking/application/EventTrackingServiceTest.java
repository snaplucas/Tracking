package com.sports.tracking.application;

import com.sports.tracking.domain.Score;
import com.sports.tracking.domain.SportEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EventTrackingServiceTest {

    private final ScoreClient scoreClient = mock(ScoreClient.class);
    private final ScorePublisher scorePublisher = mock(ScorePublisher.class);

    private EventTrackingService newService(long intervalMs) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.initialize();
        return new EventTrackingService(scheduler, scoreClient, scorePublisher, Duration.ofMillis(intervalMs));
    }

    @Test
    void marksEventLiveAndNotLive() {
        EventTrackingService service = newService(10_000);

        service.updateStatus("1234", true);
        assertThat(service.isLive("1234")).isTrue();
        assertThat(service.liveEvents()).containsExactly("1234");

        service.updateStatus("1234", false);
        assertThat(service.isLive("1234")).isFalse();
        assertThat(service.liveEvents()).isEmpty();
    }

    @Test
    void markingLiveTwiceDoesNotDoubleSchedule() {
        EventTrackingService service = newService(10_000);

        service.updateStatus("1234", true);
        service.updateStatus("1234", true);

        assertThat(service.liveEvents()).hasSize(1);
    }

    @Test
    void pollBuildsLiveScoreUpdateFromFeedAndPublishes() {
        EventTrackingService service = newService(10_000);
        when(scoreClient.getCurrentScore("1234")).thenReturn(Optional.of(Score.of("2:1")));

        service.pollOnce("1234");

        ArgumentCaptor<SportEvent> captor = ArgumentCaptor.forClass(SportEvent.class);
        verify(scorePublisher).publish(captor.capture());
        SportEvent update = captor.getValue();
        assertThat(update.eventId()).isEqualTo("1234");
        assertThat(update.score().value()).isEqualTo("2:1");
        assertThat(update.status().name()).isEqualTo("LIVE");
        assertThat(update.observedAt()).isNotNull();
    }

    @Test
    void pollDoesNotPublishWhenFeedHasNoScore() {
        EventTrackingService service = newService(10_000);
        when(scoreClient.getCurrentScore("1234")).thenReturn(Optional.empty());

        service.pollOnce("1234");

        verifyNoInteractions(scorePublisher);
    }

    @Test
    void pollSwallowsFeedErrorsAndDoesNotPublish() {
        EventTrackingService service = newService(10_000);
        when(scoreClient.getCurrentScore("1234")).thenThrow(new RuntimeException("boom"));

        service.pollOnce("1234"); // must not throw

        verifyNoInteractions(scorePublisher);
    }

    @Test
    void liveEventIsPolledRepeatedlyBySchedule() {
        EventTrackingService service = newService(50);
        when(scoreClient.getCurrentScore("1234")).thenReturn(Optional.of(Score.of("0:0")));

        service.updateStatus("1234", true);

        // The scheduler should drive several polls -> several publishes.
        verify(scorePublisher, timeout(2_000).atLeast(2)).publish(any(SportEvent.class));
    }

    @Test
    void markingNotLiveStopsFurtherPolling() throws InterruptedException {
        EventTrackingService service = newService(50);
        when(scoreClient.getCurrentScore("1234")).thenReturn(Optional.of(Score.of("0:0")));

        service.updateStatus("1234", true);
        verify(scorePublisher, timeout(2_000).atLeastOnce()).publish(any(SportEvent.class));

        service.updateStatus("1234", false);
        clearInvocations(scorePublisher);

        Thread.sleep(300); // several poll intervals
        verifyNoInteractions(scorePublisher);
    }

    @Test
    void tracksMultipleEventsIndependently() {
        EventTrackingService service = newService(10_000);

        service.updateStatus("a", true);
        service.updateStatus("b", true);
        assertThat(service.liveEvents()).containsExactlyInAnyOrder("a", "b");

        service.updateStatus("a", false);
        assertThat(service.liveEvents()).containsExactly("b");
        assertThat(service.isLive("a")).isFalse();
        assertThat(service.isLive("b")).isTrue();
    }
}
