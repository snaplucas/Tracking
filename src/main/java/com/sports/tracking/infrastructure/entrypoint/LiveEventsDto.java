package com.sports.tracking.infrastructure.entrypoint;

import lombok.Builder;

@Builder
public record LiveEventsDto(String eventId, boolean isLive) {}
