package com.example.certificate_service.dto;

public record CertificateRequest(
        String eventId,
        String subscriptionId,
        String participantName,
        String participantEmail,
        String eventName
) {
}