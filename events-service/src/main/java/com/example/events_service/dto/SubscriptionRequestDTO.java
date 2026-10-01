package com.example.events_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SubscriptionRequestDTO(
        @Schema(example = "john.doe@email.com")
        String participantEmail
) {
}
