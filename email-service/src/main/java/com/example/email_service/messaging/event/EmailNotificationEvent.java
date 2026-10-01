package com.example.email_service.messaging.event;

import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EmailNotificationEvent(
        String messageId,
        String correlationId,
        String templateId,
        String recipientEmail,
        Map<String, Object> templateData,
        Instant occurredAt
) {
    public static EmailNotificationEvent of(String templateId, String recipientEmail, Map<String, Object> templateData) {
        return new EmailNotificationEvent(
                UUID.randomUUID().toString(),
                MDC.get("correlationId"),
                templateId,
                recipientEmail,
                templateData,
                Instant.now()
        );
    }
}