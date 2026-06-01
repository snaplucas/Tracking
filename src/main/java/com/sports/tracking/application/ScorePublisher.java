package com.sports.tracking.application;

import com.sports.tracking.domain.ScoreUpdate;

public interface ScorePublisher {

    void publish(ScoreUpdate update);
}
