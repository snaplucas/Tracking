package com.sports.tracking.infrastructure.entrypoint;

import jakarta.validation.constraints.NotNull;

/**
 * Body of a status update for an event: {@code {"live": true}} or
 * {@code {"live": false}}.
 */
public record EventStatusDto(@NotNull Boolean live) {
}
