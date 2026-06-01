package com.sports.tracking.application;

import com.sports.tracking.domain.Score;

import java.util.Optional;

public interface ScoreFeedClient {

    Optional<Score> getCurrentScore(String eventId);
}
