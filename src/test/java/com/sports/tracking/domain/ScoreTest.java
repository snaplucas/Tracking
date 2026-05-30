package com.sports.tracking.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoreTest {

    @Test
    void ofCreatesScoreWithGivenValue() {
        Score score = Score.of("2:1");

        assertThat(score.value()).isEqualTo("2:1");
        assertThat(score).hasToString("2:1");
    }

    @Test
    void scoresWithSameValueAreEqual() {
        assertThat(Score.of("0:0")).isEqualTo(new Score("0:0"));
        assertThat(Score.of("0:0")).hasSameHashCodeAs(new Score("0:0"));
    }

    @Test
    void rejectsNullValue() {
        assertThatThrownBy(() -> Score.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void rejectsBlankValue() {
        assertThatThrownBy(() -> Score.of("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }
}
