package com.example.events_service.controller;

import com.example.events_service.domain.Event;
import com.example.events_service.dto.EventRequestDTO;
import com.example.events_service.exception.AlreadySubscribedException;
import com.example.events_service.exception.EventFinishedException;
import com.example.events_service.exception.EventFullException;
import com.example.events_service.exception.EventNotFoundException;
import com.example.events_service.service.EventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.Mockito.verifyNoInteractions;
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

        @ParameterizedTest
        @ValueSource(strings = {
                "",
                "{",
                "{\"maxParticipants\":\"invalid\"}",
                "{\"maxParticipants\":null}"
        })
        void returnsStructuredErrorWhenRequestCannotBeRead(String body) throws Exception {
            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message")
                            .value("O corpo da requisição está ausente ou contém dados inválidos."));

            verifyNoInteractions(eventService);
        }

        @Test
        @DisplayName("returns 400 with an explicit message when event data is missing")
        void returns400WhenEventDataIsMissing() throws Exception {
            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"maxParticipants\": 10, \"title\": \"Workshop\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("A data do evento é obrigatória."));

            verifyNoInteractions(eventService);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "{\"maxParticipants\":0,\"date\":\"2030-06-01T10:00:00Z\",\"title\":\"Workshop\"}",
                "{\"maxParticipants\":10,\"date\":\"2030-06-01T10:00:00Z\",\"title\":\" \"}",
                "{}"
        })
        void rejectsInvalidEventBeforeCallingService(String body) throws Exception {
            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").isNotEmpty());

            verifyNoInteractions(eventService);
        }

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

        @ParameterizedTest
        @ValueSource(strings = {
                "{}",
                "{\"participantEmail\":null}",
                "{\"participantEmail\":\" \"}",
                "{\"participantEmail\":\"invalid-email\"}"
        })
        void rejectsInvalidSubscriptionBeforeCallingService(String body) throws Exception {
            mockMvc.perform(post("/events/evt-001/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));

            verifyNoInteractions(eventService);
        }

        @Test
        void allowsSubscriptionWithoutOptionalParticipantName() throws Exception {
            mockMvc.perform(post("/events/evt-001/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"participantEmail\":\"user@example.com\"}"))
                    .andExpect(status().isOk());
        }

        private static final String REGISTER_BODY = """
                {"participantName": "User Example", "participantEmail": "user@example.com"}
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
                    .given(eventService).registerParticipant(eq("unknown-id"), anyString(), anyString());

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
                    .given(eventService).registerParticipant(anyString(), anyString(), anyString());

            mockMvc.perform(post("/events/evt-001/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REGISTER_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        @DisplayName("returns 409 when event has finished")
        void returns409WhenEventFinished() throws Exception {
            willThrow(new EventFinishedException())
                    .given(eventService).registerParticipant(anyString(), anyString(), anyString());

            mockMvc.perform(post("/events/evt-001/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REGISTER_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value("Evento já finalizado!"));
        }

        @Test
        @DisplayName("returns 409 when event has reached its maximum capacity")
        void returns409WhenEventFull() throws Exception {
            willThrow(new EventFullException())
                    .given(eventService).registerParticipant(anyString(), anyString(), anyString());

            mockMvc.perform(post("/events/evt-001/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REGISTER_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }
    }
}
