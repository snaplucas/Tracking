package com.sports.tracking.infrastructure.web;

import jakarta.validation.constraints.NotNull;

/**
 * Body of a status update for an event: {@code {"live": true}} or
 * {@code {"live": false}}.
 */
public record EventStatusRequest(@NotNull Boolean live) {
}
