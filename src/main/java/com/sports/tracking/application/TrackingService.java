package com.sports.tracking.application;

import java.util.Set;

public interface TrackingService {

    boolean updateStatus(String eventId, boolean live);

    Set<String> liveEvents();

    boolean isLive(String eventId);
}
