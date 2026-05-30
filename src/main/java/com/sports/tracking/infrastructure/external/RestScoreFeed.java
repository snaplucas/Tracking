package com.sports.tracking.infrastructure.external;

import com.sports.tracking.domain.Score;
import com.sports.tracking.domain.ScoreFeed;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Infrastructure adapter implementing the {@link ScoreFeed} port over HTTP.
 * Calls the external scores API and maps its {@link ExternalEventResponse} wire
 * format into the domain {@link Score} value object.
 */
@Component
@RequiredArgsConstructor
public class RestScoreFeed implements ScoreFeed {

    private final RestClient externalApiClient;

    @Override
    public Optional<Score> currentScore(String eventId) {
        ExternalEventResponse response = externalApiClient.get()
                .uri("/mock/external/events/{eventId}", eventId)
                .retrieve()
                .body(ExternalEventResponse.class);

        if (response == null || response.currentScore() == null || response.currentScore().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Score.of(response.currentScore()));
    }
}
