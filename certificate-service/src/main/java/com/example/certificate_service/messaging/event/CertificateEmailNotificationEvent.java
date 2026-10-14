package com.example.certificate_service.messaging.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CertificateEmailNotificationEvent(
        String messageId,
        String correlationId,
        String templateId,
        String recipientEmail,
        Map<String, Object> templateData,
        Instant occurredAt
) {
    public static CertificateEmailNotificationEvent of(String recipientEmail, Map<String, Object> templateData) {
        return new CertificateEmailNotificationEvent(
                UUID.randomUUID().toString(),
                null,
                "certificate",
                recipientEmail,
                templateData,
                Instant.now()
        );
    }
}