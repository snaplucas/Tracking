package com.sports.tracking.infrastructure.web;

import com.sports.tracking.application.EventTrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * Inbound web adapter: receives live ↔ not-live status updates for events and
 * drives the {@link EventTrackingService} application service.
 *
 * <pre>
 * PUT /api/events/{eventId}/status   body: {"live": true}    -> start tracking
 * PUT /api/events/{eventId}/status   body: {"live": false}   -> stop tracking
 * GET /api/events                                            -> list live events
 * GET /api/events/{eventId}                                  -> status of one event
 * </pre>
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventStatusController {

    private final EventTrackingService trackingService;

    @PutMapping("/{eventId}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable String eventId,
                                                            @Valid @RequestBody EventStatusRequest request) {
        boolean live = trackingService.updateStatus(eventId, request.live());
        return ResponseEntity.ok(Map.of("eventId", eventId, "live", live));
    }

    @GetMapping
    public Map<String, Object> liveEvents() {
        Set<String> events = trackingService.liveEvents();
        return Map.of("count", events.size(), "liveEvents", events);
    }

    @GetMapping("/{eventId}")
    public Map<String, Object> status(@PathVariable String eventId) {
        return Map.of("eventId", eventId, "live", trackingService.isLive(eventId));
    }
}
