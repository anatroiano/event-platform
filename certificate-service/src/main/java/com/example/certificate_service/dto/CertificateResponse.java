package com.example.certificate_service.dto;

import com.example.certificate_service.domain.Certificate;

import java.time.Instant;

public record CertificateResponse(
        String id,
        String eventName,
        Instant issuedAt,
        String participantName
) {

    public static CertificateResponse from(Certificate certificate) {
        return new CertificateResponse(
                certificate.getId(),
                certificate.getEventName(),
                certificate.getIssuedAt(),
                certificate.getParticipantName()
        );
    }
}
