package com.example.events_service.service;

import com.example.events_service.TestcontainersConfiguration;
import com.example.events_service.domain.Event;
import com.example.events_service.domain.OutboxMessage;
import com.example.events_service.domain.Subscription;
import com.example.events_service.dto.EventRequestDTO;
import com.example.events_service.enums.OutboxStatus;
import com.example.events_service.exception.AlreadySubscribedException;
import com.example.events_service.exception.EventFullException;
import com.example.events_service.exception.EventNotFoundException;
import com.example.events_service.repository.EventRepository;
import com.example.events_service.repository.OutboxRepository;
import com.example.events_service.repository.SubscriptionRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({
        EventService.class,
        OutboxService.class,
        EventServiceIT.TestConfig.class,
        TestcontainersConfiguration.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("EventService — integration")
class EventServiceIT {

    @TestConfiguration
    static class TestConfig {

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    private Event persistedEvent;

    @BeforeEach
    void persistEvent() {
        EventRequestDTO dto = new EventRequestDTO(
                10,
                Instant.parse("2030-06-01T10:00:00Z"),
                "Spring Boot Workshop",
                "A hands-on workshop"
        );
        persistedEvent = eventRepository.save(Event.from(dto));
    }

    @Nested
    @DisplayName("registerParticipant()")
    class RegisterParticipant {

        @Test
        @DisplayName("persists subscription and creates PENDING outbox message in the same transaction")
        void persistsSubscriptionAndCreatesOutboxMessage() {
            eventService.registerParticipant(persistedEvent.getId(), "user@example.com");

            List<Subscription> subscriptions = subscriptionRepository.findAll();
            assertThat(subscriptions).hasSize(1);
            assertThat(subscriptions.get(0).getParticipantEmail()).isEqualTo("user@example.com");
            assertThat(subscriptions.get(0).getCheckinToken()).isNotBlank();

            List<OutboxMessage> outboxMessages = outboxRepository.findAll();
            assertThat(outboxMessages).hasSize(1);
            assertThat(outboxMessages.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(outboxMessages.get(0).getRoutingKey()).isEqualTo("email.subscription.created");
            assertThat(outboxMessages.get(0).getPayload()).contains("user@example.com");
        }

        @Test
        @DisplayName("throws EventNotFoundException when the event id does not exist in the database")
        void throwsWhenEventIdNotFound() {
            assertThatThrownBy(() -> eventService.registerParticipant("non-existent-id", "user@example.com"))
                    .isInstanceOf(EventNotFoundException.class);

            assertThat(subscriptionRepository.findAll()).isEmpty();
            assertThat(outboxRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("throws AlreadySubscribedException when the same email is registered twice")
        void throwsWhenEmailAlreadyRegistered() {
            eventService.registerParticipant(persistedEvent.getId(), "user@example.com");

            assertThatThrownBy(() -> eventService.registerParticipant(persistedEvent.getId(), "user@example.com"))
                    .isInstanceOf(AlreadySubscribedException.class);

            assertThat(subscriptionRepository.countByEventId(persistedEvent.getId())).isEqualTo(1);
        }

        @Test
        @DisplayName("throws EventFullException when event has reached its maximum capacity")
        void throwsWhenEventIsAtCapacity() {
            EventRequestDTO dto = new EventRequestDTO(
                    1,
                    Instant.parse("2030-06-01T10:00:00Z"),
                    "Small Event",
                    "Only one spot"
            );
            Event smallEvent = eventRepository.save(Event.from(dto));

            eventService.registerParticipant(smallEvent.getId(), "first@example.com");

            assertThatThrownBy(() -> eventService.registerParticipant(smallEvent.getId(), "second@example.com"))
                    .isInstanceOf(EventFullException.class);

            assertThat(subscriptionRepository.countByEventId(smallEvent.getId())).isEqualTo(1);
        }

        @Test
        @DisplayName("generates a unique checkin token for each subscription")
        void generatesUniqueCheckinTokenPerSubscription() {
            EventRequestDTO dto = new EventRequestDTO(
                    10,
                    Instant.parse("2030-06-01T10:00:00Z"),
                    "Another Event",
                    "Description"
            );
            Event secondEvent = eventRepository.save(Event.from(dto));

            eventService.registerParticipant(persistedEvent.getId(), "user1@example.com");
            eventService.registerParticipant(secondEvent.getId(), "user1@example.com");

            List<Subscription> subscriptions = subscriptionRepository.findAll();
            assertThat(subscriptions).hasSize(2);

            String token1 = subscriptions.get(0).getCheckinToken();
            String token2 = subscriptions.get(1).getCheckinToken();
            assertThat(token1).isNotEqualTo(token2);
        }
    }

    @Nested
    @DisplayName("checkIn()")
    class CheckIn {

        @Test
        @DisplayName("marks the subscription as checked-in and persists the change")
        void marksSubscriptionCheckedInAndPersists() {
            eventService.registerParticipant(persistedEvent.getId(), "user@example.com");

            Subscription subscription = subscriptionRepository.findAll().get(0);
            assertThat(subscription.isCheckedIn()).isFalse();

            eventService.checkIn(subscription.getCheckinToken());

            Subscription updated = subscriptionRepository.findById(subscription.getId()).orElseThrow();
            assertThat(updated.isCheckedIn()).isTrue();
            assertThat(updated.getCheckedInAt()).isNotNull();
        }

        @Test
        @DisplayName("is idempotent: checkedInAt does not change on second call")
        void isIdempotentOnSecondCall() {
            eventService.registerParticipant(persistedEvent.getId(), "user@example.com");

            Subscription subscription = subscriptionRepository.findAll().get(0);
            String token = subscription.getCheckinToken();

            eventService.checkIn(token);
            Instant firstCheckinTime = subscriptionRepository.findById(subscription.getId())
                    .orElseThrow()
                    .getCheckedInAt();

            eventService.checkIn(token);
            Instant secondCheckinTime = subscriptionRepository.findById(subscription.getId())
                    .orElseThrow()
                    .getCheckedInAt();

            assertThat(secondCheckinTime).isEqualTo(firstCheckinTime);
        }
    }
}
