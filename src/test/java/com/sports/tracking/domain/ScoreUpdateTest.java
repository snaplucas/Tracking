package com.sports.tracking.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoreUpdateTest {

    private static final Instant AT = Instant.parse("2026-05-30T12:00:00Z");

    @Test
    void liveFactoryBuildsOfLiveUpdate() {
        ScoreUpdate update = ScoreUpdate.ofLive("1234", Score.of("1:0"), AT);

        assertThat(update.eventId()).isEqualTo("1234");
        assertThat(update.score()).isEqualTo(Score.of("1:0"));
        assertThat(update.status()).isEqualTo(EventStatus.LIVE);
        assertThat(update.observedAt()).isEqualTo(AT);
    }

    @Test
    void preservesExplicitStatus() {
        ScoreUpdate update = new ScoreUpdate("1234", Score.of("0:0"), EventStatus.NOT_LIVE, AT);

        assertThat(update.status()).isEqualTo(EventStatus.NOT_LIVE);
    }

    @Test
    void rejectsNullEventId() {
        assertThatThrownBy(() -> new ScoreUpdate(null, Score.of("0:0"), EventStatus.LIVE, AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void rejectsBlankEventId() {
        assertThatThrownBy(() -> ScoreUpdate.ofLive("  ", Score.of("0:0"), AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }
}
