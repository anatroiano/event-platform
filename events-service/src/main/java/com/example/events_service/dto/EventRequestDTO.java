package com.example.events_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record EventRequestDTO(
        @Schema(example = "100")
        int maxParticipants,
        
        @Schema(example = "2026-10-15T10:00:00Z")
        Instant date,
        
        @Schema(example = "Spring Boot Workshop")
        String title,
        
        @Schema(example = "A hands-on workshop about Spring Boot 3")
        String description
) {
}
