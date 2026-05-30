package com.sports.tracking.infrastructure.web;

import com.sports.tracking.infrastructure.external.ExternalEventResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stand-in for the real external scores API so the service is self-contained
 * and runnable without any third party. It returns the exact contract given in
 * the brief:
 *
 * <pre>
 * { "eventId": "1234", "currentScore": "0:0" }
 * </pre>
 *
 * <p>To keep demos lively, each event's score slowly evolves: every few polls a
 * goal is added to one side.
 */
@RestController
@RequestMapping("/mock/external")
public class MockExternalApiController {

    private final ConcurrentHashMap<String, int[]> scores = new ConcurrentHashMap<>();

    @GetMapping("/events/{eventId}")
    public ExternalEventResponse currentScore(@PathVariable String eventId) {
        int[] score = scores.computeIfAbsent(eventId, id -> new int[]{0, 0});
        // ~1-in-3 chance a goal is scored on any given poll.
        if (ThreadLocalRandom.current().nextInt(3) == 0) {
            score[ThreadLocalRandom.current().nextInt(2)]++;
        }
        return new ExternalEventResponse(eventId, score[0] + ":" + score[1]);
    }
}
