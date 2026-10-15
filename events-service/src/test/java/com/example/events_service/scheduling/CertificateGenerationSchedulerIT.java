package com.example.events_service.scheduling;

import com.example.events_service.TestcontainersConfiguration;
import com.example.events_service.domain.Event;
import com.example.events_service.domain.OutboxMessage;
import com.example.events_service.domain.Subscription;
import com.example.events_service.dto.EventRequestDTO;
import com.example.events_service.enums.OutboxStatus;
import com.example.events_service.repository.EventRepository;
import com.example.events_service.repository.OutboxRepository;
import com.example.events_service.repository.SubscriptionRepository;
import com.example.events_service.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        CertificateGenerationScheduler.class,
        OutboxService.class,
        CertificateGenerationSchedulerIT.TestConfig.class,
        TestcontainersConfiguration.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("CertificateGenerationScheduler — integration")
class CertificateGenerationSchedulerIT {

    @TestConfiguration
    static class TestConfig {
        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }

    @Autowired
    private CertificateGenerationScheduler scheduler;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    private Event finishedEvent;
    private Event futureEvent;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        subscriptionRepository.deleteAll();
        eventRepository.deleteAll();

        finishedEvent = eventRepository.save(Event.from(new EventRequestDTO(
                10,
                Instant.parse("2020-01-01T00:00:00Z"),
                "Past Workshop",
                "An event in the past"
        )));

        futureEvent = eventRepository.save(Event.from(new EventRequestDTO(
                10,
                Instant.parse("2099-01-01T00:00:00Z"),
                "Future Workshop",
                "An event in the future"
        )));
    }

    @Nested
    @DisplayName("requestCertificatesForFinishedEvents()")
    class RequestCertificatesForFinishedEvents {

        @Test
        @DisplayName("does nothing when all finished events already had certificate requests sent")
        void doesNothingWhenNoEligibleEvents() {
            finishedEvent.setCertificateRequestsSent(true);
            eventRepository.save(finishedEvent);

            scheduler.requestCertificatesForFinishedEvents();

            assertThat(outboxRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("does not create outbox messages for future events")
        void ignoresFutureEvents() {
            Subscription sub = subscriptionRepository.save(Subscription.from(futureEvent, "user@example.com", "User"));
            sub.checkIn();
            subscriptionRepository.save(sub);

            scheduler.requestCertificatesForFinishedEvents();

            assertThat(outboxRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("creates one PENDING outbox message per checked-in participant of a finished event")
        void createsOutboxMessageForEachCheckedInParticipant() {
            Subscription sub1 = Subscription.from(finishedEvent, "user1@example.com", "User One");
            sub1.checkIn();
            Subscription sub2 = Subscription.from(finishedEvent, "user2@example.com", "User Two");
            sub2.checkIn();
            subscriptionRepository.saveAll(List.of(sub1, sub2));

            scheduler.requestCertificatesForFinishedEvents();

            List<OutboxMessage> messages = outboxRepository.findAll();
            assertThat(messages).hasSize(2);
            assertThat(messages).allMatch(m -> m.getStatus() == OutboxStatus.PENDING);
            assertThat(messages).allMatch(m -> m.getRoutingKey().equals("certificate.generation.requested"));
        }

        @Test
        @DisplayName("outbox payload contains the correct participant and event information")
        void outboxPayloadContainsCorrectData() {
            Subscription sub = Subscription.from(finishedEvent, "john.doe@email.com", "John Doe");
            sub.checkIn();
            subscriptionRepository.save(sub);

            scheduler.requestCertificatesForFinishedEvents();

            String payload = outboxRepository.findAll().get(0).getPayload();
            assertThat(payload).contains("john.doe@email.com");
            assertThat(payload).contains("John Doe");
            assertThat(payload).contains("Past Workshop");
            assertThat(payload).contains(finishedEvent.getId());
        }

        @Test
        @DisplayName("does not create outbox messages for subscriptions that were not checked in")
        void ignoresSubscriptionsWithoutCheckin() {
            subscriptionRepository.save(Subscription.from(finishedEvent, "user@example.com", "User"));

            scheduler.requestCertificatesForFinishedEvents();

            assertThat(outboxRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("marks finished event as certificateRequestsSent=true after processing")
        void marksCertificateRequestsSentOnEvent() {
            Subscription sub = Subscription.from(finishedEvent, "user@example.com", "User");
            sub.checkIn();
            subscriptionRepository.save(sub);

            scheduler.requestCertificatesForFinishedEvents();

            Event updated = eventRepository.findById(finishedEvent.getId()).orElseThrow();
            assertThat(updated.isCertificateRequestsSent()).isTrue();
        }

        @Test
        @DisplayName("marks finished event even when it has no checked-in participants")
        void marksEventEvenWithNoCheckedInParticipants() {
            scheduler.requestCertificatesForFinishedEvents();

            Event updated = eventRepository.findById(finishedEvent.getId()).orElseThrow();
            assertThat(updated.isCertificateRequestsSent()).isTrue();
            assertThat(outboxRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("second scheduler run does not re-process already-flagged events")
        void secondRunDoesNotReprocessFlaggedEvent() {
            Subscription sub = Subscription.from(finishedEvent, "user@example.com", "User");
            sub.checkIn();
            subscriptionRepository.save(sub);

            scheduler.requestCertificatesForFinishedEvents();
            scheduler.requestCertificatesForFinishedEvents();

            assertThat(outboxRepository.findAll()).hasSize(1);
        }
    }
}
