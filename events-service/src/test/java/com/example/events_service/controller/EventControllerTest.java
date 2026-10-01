package com.example.events_service.controller;

import com.example.events_service.domain.Event;
import com.example.events_service.dto.EventRequestDTO;
import com.example.events_service.exception.AlreadySubscribedException;
import com.example.events_service.exception.EventFullException;
import com.example.events_service.exception.EventNotFoundException;
import com.example.events_service.service.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EventService eventService;

    @Nested
    @DisplayName("POST /events")
    class CreateEvent {

        @Test
        @DisplayName("should return 201 with the created event")
        void shouldReturn201WithCreatedEvent() throws Exception {
            EventRequestDTO dto = new EventRequestDTO(50, Instant.now().plusSeconds(3600), "Java Summit", "Descrição");
            Event event = Event.from(dto);

            when(eventService.create(any(EventRequestDTO.class))).thenReturn(event);

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.title").value("Java Summit"));
        }
    }

    @Nested
    @DisplayName("GET /events")
    class GetAll {

        @Test
        @DisplayName("should return 200 with the paginated list")
        void shouldReturn200WithPage() throws Exception {
            when(eventService.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/events"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }
    }

    @Nested
    @DisplayName("GET /events/upcoming")
    class GetUpcoming {

        @Test
        @DisplayName("should return 200 with the paginated list of upcoming events")
        void shouldReturn200WithPage() throws Exception {
            when(eventService.findUpcoming(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/events/upcoming"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }
    }

    @Nested
    @DisplayName("POST /events/{eventId}/register")
    class RegisterParticipant {

        @Test
        @DisplayName("should return 200 when registration is successful")
        void shouldReturn200WhenSuccessful() throws Exception {
            doNothing().when(eventService).registerParticipant(eq("evt-1"), eq("user@example.com"));

            mockMvc.perform(post("/events/evt-1/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "participantEmail": "user@example.com" }
                                    """))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 404 when the event does not exist")
        void shouldReturn404WhenEventNotFound() throws Exception {
            doThrow(new EventNotFoundException())
                    .when(eventService).registerParticipant(eq("missing"), anyString());

            mockMvc.perform(post("/events/missing/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "participantEmail": "user@example.com" }
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("should return 409 when participant is already subscribed")
        void shouldReturn409WhenAlreadySubscribed() throws Exception {
            doThrow(new AlreadySubscribedException())
                    .when(eventService).registerParticipant(anyString(), anyString());

            mockMvc.perform(post("/events/evt-1/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "participantEmail": "user@example.com" }
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        @DisplayName("should return 409 when the event is full")
        void shouldReturn409WhenEventFull() throws Exception {
            doThrow(new EventFullException())
                    .when(eventService).registerParticipant(anyString(), anyString());

            mockMvc.perform(post("/events/evt-1/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "participantEmail": "user@example.com" }
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }
    }
}
