package com.example.certificate_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CertificateRequest(
        @NotBlank(message = "O identificador do evento é obrigatório.")
        @Size(max = 255, message = "O identificador do evento deve ter no máximo 255 caracteres.")
        String eventId,
        @NotBlank(message = "O identificador da inscrição é obrigatório.")
        @Size(max = 255, message = "O identificador da inscrição deve ter no máximo 255 caracteres.")
        String subscriptionId,
        @NotBlank(message = "O nome do participante é obrigatório.")
        @Size(max = 255, message = "O nome do participante deve ter no máximo 255 caracteres.")
        String participantName,
        @NotBlank(message = "O e-mail do participante é obrigatório.")
        @Email(message = "O e-mail do participante deve ser válido.")
        @Size(max = 255, message = "O e-mail do participante deve ter no máximo 255 caracteres.")
        String participantEmail,
        @NotBlank(message = "O nome do evento é obrigatório.")
        @Size(max = 255, message = "O nome do evento deve ter no máximo 255 caracteres.")
        String eventName
) {
}
