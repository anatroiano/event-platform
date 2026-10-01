package com.example.events_service.controller;

import com.example.events_service.dto.CheckinInfoDTO;
import com.example.events_service.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Check-in", description = "Operations for participant event check-in")
@RestController
@RequestMapping("/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final EventService eventService;

    @Operation(summary = "Get check-in information", description = "Retrieves the event information associated with a participant's unique check-in token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Check-in information retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Check-in token not found")
    })
    @GetMapping("/{token}")
    public ResponseEntity<CheckinInfoDTO> getInfo(@PathVariable String token) {
        return ResponseEntity.ok(eventService.getCheckinInfo(token));
    }

    @Operation(summary = "Confirm participant check-in", description = "Confirms the participant's attendance using the unique check-in token.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Participant check-in confirmed successfully"),
            @ApiResponse(responseCode = "404", description = "Check-in token not found")
    })
    @PostMapping("/{token}")
    public ResponseEntity<Void> confirm(@PathVariable String token) {
        eventService.checkIn(token);

        return ResponseEntity.noContent().build();
    }
}