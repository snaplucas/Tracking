package com.sports.tracking.infrastructure.entrypoint;

import jakarta.validation.constraints.NotNull;

public record EventStatusDto(@NotNull Boolean live) {
}
