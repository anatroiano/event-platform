package com.example.email_service.messaging.event;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EmailNotificationEvent(
        @NotBlank(message = "O identificador da mensagem é obrigatório.")
        @Size(max = 64, message = "O identificador da mensagem deve ter no máximo 64 caracteres.")
        String messageId,
        String correlationId,
        @NotBlank(message = "O template do e-mail é obrigatório.")
        String templateId,
        @NotBlank(message = "O destinatário do e-mail é obrigatório.")
        @Email(message = "O e-mail do destinatário deve ser válido.")
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
