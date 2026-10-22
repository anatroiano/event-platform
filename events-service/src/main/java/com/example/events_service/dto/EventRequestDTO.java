package com.example.events_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record EventRequestDTO(
        @Schema(example = "100")
        @Positive(message = "A capacidade do evento deve ser maior que zero.")
        int maxParticipants,

        @Schema(example = "2026-10-15T10:00:00Z")
        @NotNull(message = "A data do evento é obrigatória.")
        Instant date,

        @Schema(example = "Spring Boot Workshop")
        @NotBlank(message = "O título do evento é obrigatório.")
        @Size(max = 255, message = "O título do evento deve ter no máximo 255 caracteres.")
        String title,

        @Schema(example = "A hands-on workshop about Spring Boot 3")
        @Size(max = 2000, message = "A descrição do evento deve ter no máximo 2000 caracteres.")
        String description
) {
}
