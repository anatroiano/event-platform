package com.example.events_service.controller;

import com.example.events_service.domain.Event;
import com.example.events_service.dto.EventRequestDTO;
import com.example.events_service.exception.AlreadySubscribedException;
import com.example.events_service.exception.EventFullException;
import com.example.events_service.exception.EventNotFoundException;
import com.example.events_service.service.EventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@DisplayName("EventController")
class EventControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    private Event buildSavedEvent(String id) {
        EventRequestDTO dto = new EventRequestDTO(
                100,
                Instant.parse("2030-06-01T10:00:00Z"),
                "Spring Boot Workshop",
                "A hands-on workshop"
        );
        Event event = Event.from(dto);
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    @Nested
    @DisplayName("POST /events")
    class CreateEvent {

        @Test
        @DisplayName("returns 201 and the created event body")
        void returns201WithEventBody() throws Exception {
            Event saved = buildSavedEvent("evt-001");
            given(eventService.create(any(EventRequestDTO.class))).willReturn(saved);

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "maxParticipants": 100,
                                      "date": "2030-06-01T10:00:00Z",
                                      "title": "Spring Boot Workshop",
                                      "description": "A hands-on workshop"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value("evt-001"))
                    .andExpect(jsonPath("$.title").value("Spring Boot Workshop"));
        }
    }

    @Nested
    @DisplayName("GET /events")
    class GetAll {

        @Test
        @DisplayName("returns 200 with a paginated list of events")
        void returns200WithPage() throws Exception {
            given(eventService.findAll(any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(buildSavedEvent("evt-001"))));

            mockMvc.perform(get("/events"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value("evt-001"));
        }
    }

    @Nested
    @DisplayName("GET /events/upcoming")
    class GetUpcoming {

        @Test
        @DisplayName("returns 200 with upcoming events")
        void returns200WithUpcomingEvents() throws Exception {
            given(eventService.findUpcoming(any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(buildSavedEvent("evt-002"))));

            mockMvc.perform(get("/events/upcoming"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value("evt-002"));
        }
    }

    @Nested
    @DisplayName("POST /events/{id}/register")
    class RegisterParticipant {

        private static final String REGISTER_BODY = """
                {"participantEmail": "user@example.com"}
                """;

        @Test
        @DisplayName("returns 200 when registration succeeds")
        void returns200OnSuccess() throws Exception {
            mockMvc.perform(post("/events/evt-001/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REGISTER_BODY))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("returns 404 when event does not exist")
        void returns404WhenEventNotFound() throws Exception {
            willThrow(new EventNotFoundException())
                    .given(eventService).registerParticipant(eq("unknown-id"), anyString());

            mockMvc.perform(post("/events/unknown-id/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REGISTER_BODY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("returns 409 when participant is already subscribed")
        void returns409WhenAlreadySubscribed() throws Exception {
            willThrow(new AlreadySubscribedException())
                    .given(eventService).registerParticipant(anyString(), anyString());

            mockMvc.perform(post("/events/evt-001/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REGISTER_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        @DisplayName("returns 409 when event has reached its maximum capacity")
        void returns409WhenEventFull() throws Exception {
            willThrow(new EventFullException())
                    .given(eventService).registerParticipant(anyString(), anyString());

            mockMvc.perform(post("/events/evt-001/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REGISTER_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }
    }
}
