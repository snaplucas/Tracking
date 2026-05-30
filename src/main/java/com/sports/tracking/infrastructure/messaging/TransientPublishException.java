package com.sports.tracking.infrastructure.messaging;

/**
 * Signals a transient failure while publishing a score message (broker
 * unavailable, send timeout, network blip). Retryable: distinct from
 * programming/serialization errors, which should not be retried.
 */
public class TransientPublishException extends RuntimeException {

    public TransientPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
