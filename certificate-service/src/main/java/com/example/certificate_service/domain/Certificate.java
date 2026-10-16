package com.example.certificate_service.domain;

import com.example.certificate_service.dto.CertificateRequest;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "certificates")
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "subscription_id", nullable = false)
    private String subscriptionId;

    @Column(name = "participant_name", nullable = false)
    private String participantName;

    @Column(name = "participant_email", nullable = false)
    private String participantEmail;

    @Column(name = "event_name", nullable = false)
    private String eventName;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "file_key")
    private String fileKey;

    protected Certificate() {
    }

    private Certificate(CertificateRequest certificateRequest) {
        this.eventId = certificateRequest.eventId();
        this.subscriptionId = certificateRequest.subscriptionId();
        this.participantName = certificateRequest.participantName();
        this.participantEmail = certificateRequest.participantEmail();
        this.eventName = certificateRequest.eventName();
        this.issuedAt = Instant.now();
    }

    public static Certificate from(CertificateRequest certificateRequest) {
        return new Certificate(certificateRequest);
    }

}
