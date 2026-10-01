package com.example.events_service.dto;

import java.time.Instant;

public record CheckinInfoDTO(String eventTitle, Instant startsAt, boolean alreadyCheckedIn) {
}