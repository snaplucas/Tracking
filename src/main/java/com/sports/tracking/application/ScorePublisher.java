package com.sports.tracking.application;

import com.sports.tracking.domain.SportEvent;

public interface ScorePublisher {

    void publish(SportEvent update);
}
