package com.sports.tracking.infrastructure.messaging;

public class TransientPublishException extends RuntimeException {

    public TransientPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
