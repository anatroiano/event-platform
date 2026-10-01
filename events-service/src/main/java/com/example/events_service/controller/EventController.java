package com.example.events_service.controller;

import com.example.events_service.domain.Event;
import com.example.events_service.dto.EventRequestDTO;
import com.example.events_service.dto.SubscriptionRequestDTO;
import com.example.events_service.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Events", description = "Operations related to event management")
@RestController
@RequestMapping("/events")
public class EventController {

    @Autowired
    private EventService eventService;

    @Operation(summary = "Create a new event", description = "Creates a new event with the provided data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Event created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data")
    })
    @PostMapping
    public ResponseEntity<Event> create(@RequestBody EventRequestDTO eventRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.create(eventRequest));
    }

    @Operation(summary = "List events", description = "Returns a paginated list of events")
    @GetMapping
    public ResponseEntity<Page<Event>> getAll(@ParameterObject Pageable pageable) {
        return ResponseEntity.ok(eventService.findAll(pageable));
    }

    @Operation(summary = "List upcoming events", description = "Returns a paginated list of upcoming events")
    @GetMapping("/upcoming")
    public ResponseEntity<Page<Event>> getUpcoming(@ParameterObject Pageable pageable) {
        return ResponseEntity.ok(eventService.findUpcoming(pageable));
    }

    @Operation(summary = "Register participant", description = "Registers a participant to a specific event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Participant registered successfully"),
            @ApiResponse(responseCode = "404", description = "Event not found"),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict: participant is already subscribed or event has reached its maximum capacity"
            )
    })
    @PostMapping("/{eventId}/register")
    public void registerParticipant(@PathVariable String eventId, @RequestBody SubscriptionRequestDTO subscriptionRequest) {
        eventService.registerParticipant(eventId, subscriptionRequest.participantEmail());
    }

}
