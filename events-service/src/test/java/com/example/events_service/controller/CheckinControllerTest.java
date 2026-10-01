package com.example.events_service.controller;

import com.example.events_service.dto.CheckinInfoDTO;
import com.example.events_service.exception.SubscriptionNotFoundException;
import com.example.events_service.service.EventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CheckinController.class)
class CheckinControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @Nested
    @DisplayName("GET /checkin/{token}")
    class GetInfo {

        @Test
        @DisplayName("should return 200 with the check-in information")
        void shouldReturn200WithCheckinInfo() throws Exception {
            CheckinInfoDTO dto = new CheckinInfoDTO("Java Summit", Instant.parse("2026-10-15T10:00:00Z"), false);
            when(eventService.getCheckinInfo("valid-token")).thenReturn(dto);

            mockMvc.perform(get("/checkin/valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventTitle").value("Java Summit"))
                    .andExpect(jsonPath("$.alreadyCheckedIn").value(false));
        }

        @Test
        @DisplayName("should return 404 when the token does not exist")
        void shouldReturn404WhenTokenNotFound() throws Exception {
            when(eventService.getCheckinInfo("bad-token")).thenThrow(new SubscriptionNotFoundException());

            mockMvc.perform(get("/checkin/bad-token"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Nested
    @DisplayName("POST /checkin/{token}")
    class Confirm {

        @Test
        @DisplayName("should return 204 when check-in is confirmed successfully")
        void shouldReturn204WhenSuccessful() throws Exception {
            doNothing().when(eventService).checkIn("valid-token");

            mockMvc.perform(post("/checkin/valid-token")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNoContent());

            verify(eventService).checkIn("valid-token");
        }

        @Test
        @DisplayName("should return 404 when the token does not exist")
        void shouldReturn404WhenTokenNotFound() throws Exception {
            doThrow(new SubscriptionNotFoundException()).when(eventService).checkIn("bad-token");

            mockMvc.perform(post("/checkin/bad-token")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }
}
