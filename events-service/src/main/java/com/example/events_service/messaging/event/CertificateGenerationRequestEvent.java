package com.example.events_service.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record CertificateGenerationRequestEvent(
        String messageId,
        String eventId,
        String eventName,
        String subscriptionId,
        String participantName,
        String participantEmail,
        Instant occurredAt
) {
    public static CertificateGenerationRequestEvent of(
            String eventId,
            String eventName,
            String subscriptionId,
            String participantName,
            String participantEmail
    ) {
        return new CertificateGenerationRequestEvent(
                UUID.randomUUID().toString(),
                eventId,
                eventName,
                subscriptionId,
                participantName,
                participantEmail,
                Instant.now()
        );
    }
}
