package com.sports.tracking.infrastructure.entrypoint;

import com.sports.tracking.application.TrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventStatusController {

    private final TrackingService trackingService;

    @PutMapping("/{eventId}/status")
    public LiveEventsDto updateStatus(@PathVariable String eventId,
                                      @Valid @RequestBody EventStatusDto request) {
        boolean live = trackingService.updateStatus(eventId, request.live());
        return LiveEventsDto.builder()
                .eventId(eventId)
                .isLive(live)
                .build();
    }

    @GetMapping
    public Map<String, Object> liveEvents() {
        Set<String> events = trackingService.liveEvents();
        return Map.of("count", events.size(), "liveEvents", events);
    }

    @GetMapping("/{eventId}")
    public LiveEventsDto status(@PathVariable String eventId) {
        return LiveEventsDto.builder()
                .eventId(eventId)
                .isLive(trackingService.isLive(eventId))
                .build();
    }
}
