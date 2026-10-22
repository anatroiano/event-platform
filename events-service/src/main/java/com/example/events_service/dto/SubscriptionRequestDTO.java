package com.example.events_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubscriptionRequestDTO(
        @Schema(example = "John Doe")
        @Size(max = 255, message = "O nome do participante deve ter no máximo 255 caracteres.")
        String participantName,
        @Schema(example = "john.doe@email.com")
        @NotBlank(message = "O e-mail do participante é obrigatório.")
        @Email(message = "O e-mail do participante deve ser válido.")
        @Size(max = 255, message = "O e-mail do participante deve ter no máximo 255 caracteres.")
        String participantEmail
) {
}
