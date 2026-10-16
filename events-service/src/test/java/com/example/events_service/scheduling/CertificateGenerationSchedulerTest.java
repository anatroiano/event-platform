package com.example.events_service.scheduling;

import com.example.events_service.domain.Event;
import com.example.events_service.domain.Subscription;
import com.example.events_service.dto.EventRequestDTO;
import com.example.events_service.repository.EventRepository;
import com.example.events_service.repository.SubscriptionRepository;
import com.example.events_service.service.OutboxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("CertificateGenerationScheduler")
class CertificateGenerationSchedulerTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private CertificateGenerationScheduler scheduler;

    private Event buildFinishedEvent(String id) {
        EventRequestDTO dto = new EventRequestDTO(
                50,
                Instant.parse("2020-01-01T00:00:00Z"),
                "Past Workshop",
                "A finished event"
        );
        Event event = Event.from(dto);
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    private Subscription buildCheckedInSubscription(Event event) {
        Subscription subscription = Subscription.from(event, "user@example.com", "User Example");
        ReflectionTestUtils.setField(subscription, "id", 1L);
        subscription.checkIn();
        return subscription;
    }

    @Nested
    @DisplayName("requestCertificatesForFinishedEvents()")
    class RequestCertificatesForFinishedEvents {

        @Test
        @DisplayName("does nothing when there are no finished events without certificate requests")
        void doesNothingWhenNoFinishedEvents() {
            given(eventRepository.findFinishedEventsWithoutCertificateRequests(any(Instant.class)))
                    .willReturn(List.of());

            scheduler.requestCertificatesForFinishedEvents();

            then(subscriptionRepository).should(never()).findByEventIdAndCheckedInAtIsNotNull(any());
            then(outboxService).should(never()).save(any(), any());
        }

        @Test
        @DisplayName("saves an outbox message for each checked-in participant in each finished event")
        void savesOutboxMessagePerCheckedInParticipant() {
            Event event = buildFinishedEvent("evt-finished");
            Subscription sub1 = buildCheckedInSubscription(event);
            Subscription sub2 = Subscription.from(event, "other@example.com", "Other User");
            ReflectionTestUtils.setField(sub2, "id", 2L);
            sub2.checkIn();

            given(eventRepository.findFinishedEventsWithoutCertificateRequests(any(Instant.class)))
                    .willReturn(List.of(event));
            given(subscriptionRepository.findByEventIdAndCheckedInAtIsNotNull("evt-finished"))
                    .willReturn(List.of(sub1, sub2));

            scheduler.requestCertificatesForFinishedEvents();

            then(outboxService).should(org.mockito.Mockito.times(2))
                    .save(eq("certificate.generation.requested"), any());
        }

        @Test
        @DisplayName("marks event as certificateRequestsSent=true after processing")
        void marksEventAsCertificateRequestsSent() {
            Event event = buildFinishedEvent("evt-finished");
            Subscription sub = buildCheckedInSubscription(event);

            given(eventRepository.findFinishedEventsWithoutCertificateRequests(any(Instant.class)))
                    .willReturn(List.of(event));
            given(subscriptionRepository.findByEventIdAndCheckedInAtIsNotNull("evt-finished"))
                    .willReturn(List.of(sub));

            scheduler.requestCertificatesForFinishedEvents();

            assertThat(event.isCertificateRequestsSent()).isTrue();
            then(eventRepository).should().save(event);
        }

        @Test
        @DisplayName("outbox payload contains the correct participant and event data")
        void outboxPayloadContainsCorrectData() {
            Event event = buildFinishedEvent("evt-finished");
            Subscription sub = buildCheckedInSubscription(event);

            given(eventRepository.findFinishedEventsWithoutCertificateRequests(any(Instant.class)))
                    .willReturn(List.of(event));
            given(subscriptionRepository.findByEventIdAndCheckedInAtIsNotNull("evt-finished"))
                    .willReturn(List.of(sub));

            scheduler.requestCertificatesForFinishedEvents();

            ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
            then(outboxService).should().save(any(), payloadCaptor.capture());

            var requestEvent = (com.example.events_service.messaging.event.CertificateGenerationRequestEvent) payloadCaptor.getValue();
            assertThat(requestEvent.eventId()).isEqualTo("evt-finished");
            assertThat(requestEvent.eventName()).isEqualTo("Past Workshop");
            assertThat(requestEvent.participantEmail()).isEqualTo("user@example.com");
            assertThat(requestEvent.participantName()).isEqualTo("User Example");
        }

        @Test
        @DisplayName("skips checked-in subscription processing when event has no checked-in participants")
        void skipsEventWithNoCheckedInParticipants() {
            Event event = buildFinishedEvent("evt-empty");

            given(eventRepository.findFinishedEventsWithoutCertificateRequests(any(Instant.class)))
                    .willReturn(List.of(event));
            given(subscriptionRepository.findByEventIdAndCheckedInAtIsNotNull("evt-empty"))
                    .willReturn(List.of());

            scheduler.requestCertificatesForFinishedEvents();

            then(outboxService).should(never()).save(any(), any());
            then(eventRepository).should().save(event);
            assertThat(event.isCertificateRequestsSent()).isTrue();
        }

        @Test
        @DisplayName("processes all finished events independently")
        void processesAllFinishedEventsIndependently() {
            Event event1 = buildFinishedEvent("evt-1");
            Event event2 = buildFinishedEvent("evt-2");
            ReflectionTestUtils.setField(event2, "id", "evt-2");

            Subscription sub1 = buildCheckedInSubscription(event1);
            Subscription sub2 = buildCheckedInSubscription(event2);

            given(eventRepository.findFinishedEventsWithoutCertificateRequests(any(Instant.class)))
                    .willReturn(List.of(event1, event2));
            given(subscriptionRepository.findByEventIdAndCheckedInAtIsNotNull("evt-1"))
                    .willReturn(List.of(sub1));
            given(subscriptionRepository.findByEventIdAndCheckedInAtIsNotNull("evt-2"))
                    .willReturn(List.of(sub2));

            scheduler.requestCertificatesForFinishedEvents();

            then(outboxService).should(org.mockito.Mockito.times(2))
                    .save(eq("certificate.generation.requested"), any());
            assertThat(event1.isCertificateRequestsSent()).isTrue();
            assertThat(event2.isCertificateRequestsSent()).isTrue();
        }
    }
}
