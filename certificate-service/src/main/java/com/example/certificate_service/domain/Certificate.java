package com.example.certificate_service.domain;

import com.example.certificate_service.dto.CertificateRequest;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "certificates", uniqueConstraints = @UniqueConstraint(
        name = "uk_certificates_subscription_event", columnNames = {"subscription_id", "event_id"}
))
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class Certificate {

    @Id
    private String id;

    @Column(name = "event_id", nullable = false)
    @NotBlank(message = "O identificador do evento é obrigatório.")
    @Size(max = 255, message = "O identificador do evento deve ter no máximo 255 caracteres.")
    private String eventId;

    @Column(name = "subscription_id", nullable = false)
    @NotBlank(message = "O identificador da inscrição é obrigatório.")
    @Size(max = 255, message = "O identificador da inscrição deve ter no máximo 255 caracteres.")
    private String subscriptionId;

    @Column(name = "participant_name", nullable = false)
    @NotBlank(message = "O nome do participante é obrigatório.")
    @Size(max = 255, message = "O nome do participante deve ter no máximo 255 caracteres.")
    private String participantName;

    @Column(name = "participant_email", nullable = false)
    @NotBlank(message = "O e-mail do participante é obrigatório.")
    @Email(message = "O e-mail do participante deve ser válido.")
    @Size(max = 255, message = "O e-mail do participante deve ter no máximo 255 caracteres.")
    private String participantEmail;

    @Column(name = "event_name", nullable = false)
    @NotBlank(message = "O nome do evento é obrigatório.")
    @Size(max = 255, message = "O nome do evento deve ter no máximo 255 caracteres.")
    private String eventName;

    @Column(name = "issued_at", nullable = false)
    @NotNull(message = "A data de emissão é obrigatória.")
    private Instant issuedAt;

    @Column(name = "file_key")
    private String fileKey;

    protected Certificate() {
    }

    private Certificate(CertificateRequest certificateRequest, Instant issuedAt) {
        this.id = UUID.randomUUID().toString();
        this.eventId = certificateRequest.eventId();
        this.subscriptionId = certificateRequest.subscriptionId();
        this.participantName = certificateRequest.participantName();
        this.participantEmail = certificateRequest.participantEmail();
        this.eventName = certificateRequest.eventName();
        this.issuedAt = issuedAt;
    }

    public static Certificate from(CertificateRequest certificateRequest, Instant issuedAt) {
        return new Certificate(certificateRequest, issuedAt);
    }

}
