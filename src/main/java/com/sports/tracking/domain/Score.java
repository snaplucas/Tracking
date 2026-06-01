package com.sports.tracking.domain;


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
