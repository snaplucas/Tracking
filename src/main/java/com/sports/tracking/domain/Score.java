package com.sports.tracking.domain;

/**
 * Value object representing the score of an event at a point in time, e.g.
 * {@code "0:0"}. Immutable and self-validating.
 */
public record Score(String value) {

    public Score {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("score value must not be blank");
        }
    }

    public static Score of(String value) {
        return new Score(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
