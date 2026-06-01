package com.sports.tracking.infrastructure.communication;

import com.sports.tracking.domain.Score;
import com.sports.tracking.application.ScoreClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MockedScoreClient implements ScoreClient {

    private final RestClient externalApiClient;

    @Override
    public Optional<Score> getCurrentScore(String eventId) {
        ExternalEventDto externalEventDto = externalApiClient.get()
                .uri("/mock/external/events/{eventId}", eventId)
                .retrieve()
                .body(ExternalEventDto.class);

        if (isNotValid(externalEventDto)) {
            return Optional.empty();
        }
        return Optional.of(Score.of(externalEventDto.currentScore()));
    }

    private static boolean isNotValid(ExternalEventDto externalEventDto) {
        return externalEventDto == null || externalEventDto.currentScore() == null || externalEventDto.currentScore().isBlank();
    }
}
