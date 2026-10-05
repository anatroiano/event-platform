package com.example.events_service.controller;

import com.example.events_service.dto.CheckinInfoDTO;
import com.example.events_service.exception.SubscriptionNotFoundException;
import com.example.events_service.service.EventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CheckinController.class)
@DisplayName("CheckinController")
class CheckinControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @Nested
    @DisplayName("GET /checkin/{token}")
    class GetCheckinInfo {

        @Test
        @DisplayName("returns 200 with check-in info for a valid token")
        void returns200WithCheckinInfo() throws Exception {
            CheckinInfoDTO info = new CheckinInfoDTO(
                    "Spring Boot Workshop",
                    Instant.parse("2030-06-01T10:00:00Z"),
                    false
            );
            given(eventService.getCheckinInfo("valid-token")).willReturn(info);

            mockMvc.perform(get("/checkin/valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventTitle").value("Spring Boot Workshop"))
                    .andExpect(jsonPath("$.alreadyCheckedIn").value(false));
        }

        @Test
        @DisplayName("returns 404 when token is not found")
        void returns404WhenTokenNotFound() throws Exception {
            given(eventService.getCheckinInfo("unknown-token"))
                    .willThrow(new SubscriptionNotFoundException());

            mockMvc.perform(get("/checkin/unknown-token"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Nested
    @DisplayName("POST /checkin/{token}")
    class ConfirmCheckin {

        @Test
        @DisplayName("returns 204 when check-in is confirmed successfully")
        void returns204OnSuccess() throws Exception {
            willDoNothing().given(eventService).checkIn("valid-token");

            mockMvc.perform(post("/checkin/valid-token")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("returns 404 when token is not found")
        void returns404WhenTokenNotFound() throws Exception {
            willThrow(new SubscriptionNotFoundException())
                    .given(eventService).checkIn("unknown-token");

            mockMvc.perform(post("/checkin/unknown-token")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }
}
