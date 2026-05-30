package com.sports.tracking.infrastructure.external;

/**
 * Wire format returned by the external (mocked) scores API. This DTO lives in
 * infrastructure as an anti-corruption boundary — it is mapped into the domain
 * {@code Score} and never leaks into the domain or application layers.
 *
 * <pre>
 * { "eventId": "1234", "currentScore": "0:0" }
 * </pre>
 */
public record ExternalEventResponse(String eventId, String currentScore) {
}
