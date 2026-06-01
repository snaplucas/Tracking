package com.sports.tracking.infrastructure.entrypoint;

import com.sports.tracking.infrastructure.communication.ExternalEventDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;


@RestController
@RequestMapping("/mock/external")
public class MockExternalApiController {

    private final ConcurrentHashMap<String, int[]> scores = new ConcurrentHashMap<>();

    @GetMapping("/events/{eventId}")
    public ExternalEventDto currentScore(@PathVariable String eventId) {
        int[] score = scores.computeIfAbsent(eventId, id -> new int[]{0, 0});
        // ~1-in-3 chance a goal is scored on any given poll.
        if (ThreadLocalRandom.current().nextInt(3) == 0) {
            score[ThreadLocalRandom.current().nextInt(2)]++;
        }
        return new ExternalEventDto(eventId, score[0] + ":" + score[1]);
    }
}
