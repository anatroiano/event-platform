package com.example.events_service.service;

import com.example.events_service.TestcontainersConfiguration;
import com.example.events_service.domain.Event;
import com.example.events_service.domain.OutboxMessage;
import com.example.events_service.domain.Subscription;
import com.example.events_service.dto.EventRequestDTO;
import com.example.events_service.enums.OutboxStatus;
import com.example.events_service.exception.AlreadySubscribedException;
import com.example.events_service.exception.EventFinishedException;
import com.example.events_service.exception.EventFullException;
import com.example.events_service.exception.EventNotFoundException;
import com.example.events_service.repository.EventRepository;
import com.example.events_service.repository.OutboxRepository;
import com.example.events_service.repository.SubscriptionRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({
        EventService.class,
        OutboxService.class,
        EventServiceIT.TestConfig.class,
        TestcontainersConfiguration.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("EventService — integration")
class EventServiceIT {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @TestConfiguration
    static class TestConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

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

    @Test
    void appliesVersionedSchemaMigrations() {
        assertThat(flyway.info().applied()).extracting(MigrationInfo::getScript)
                .contains("V1__create_initial_schema.sql");
        assertThat(flyway.info().pending()).isEmpty();
        flyway.validate();
    }

    @Test
    @DisplayName("rejects an outbox write outside a business transaction")
    void rejectsOutboxWriteWithoutTransaction() {
        TransactionTemplate withoutTransaction = new TransactionTemplate(transactionManager);
        withoutTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);

        withoutTransaction.executeWithoutResult(status ->
                assertThatThrownBy(() -> outboxService.save("email.subscription.created", "payload"))
                        .isInstanceOf(IllegalTransactionStateException.class));

        assertThat(outboxRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("validates event constraints before writing to the database")
    void rejectsInvalidEventOnPersistence() {
        Event event = Event.from(new EventRequestDTO(10, null, "Workshop", null));

        assertThatThrownBy(() -> {
            entityManager.persist(event);
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("validates subscription constraints before writing to the database")
    void rejectsInvalidSubscriptionOnPersistence() {
        Subscription subscription = Subscription.from(persistedEvent, "invalid-email", null);

        assertThatThrownBy(() -> {
            entityManager.persist(subscription);
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
    }

    @Nested
    @DisplayName("registerParticipant()")
    class RegisterParticipant {

        @Test
        @DisplayName("rejects registration at the deadline without storing a subscription or notification")
        void rejectsRegistrationAtDeadline() {
            persistedEvent.setDate(NOW);
            eventRepository.saveAndFlush(persistedEvent);

            assertThatThrownBy(() -> eventService.registerParticipant(persistedEvent.getId(),
                    "user@example.com", "User Example"))
                    .isInstanceOf(EventFinishedException.class);

            assertThat(subscriptionRepository.findAll()).isEmpty();
            assertThat(outboxRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("persists subscription and creates PENDING outbox message in the same transaction")
        void persistsSubscriptionAndCreatesOutboxMessage() {
            eventService.registerParticipant(persistedEvent.getId(), "user@example.com", "User Example");

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
            assertThatThrownBy(() -> eventService.registerParticipant("non-existent-id", "user@example.com", "User Example"))
                    .isInstanceOf(EventNotFoundException.class);

            assertThat(subscriptionRepository.findAll()).isEmpty();
            assertThat(outboxRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("throws AlreadySubscribedException when the same email is registered twice")
        void throwsWhenEmailAlreadyRegistered() {
            eventService.registerParticipant(persistedEvent.getId(), "user@example.com", "User Example");

            assertThatThrownBy(() -> eventService.registerParticipant(persistedEvent.getId(), "user@example.com", "User Example"))
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

            eventService.registerParticipant(smallEvent.getId(), "first@example.com", "First User");

            assertThatThrownBy(() -> eventService.registerParticipant(smallEvent.getId(), "second@example.com", "Second User"))
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

            eventService.registerParticipant(persistedEvent.getId(), "user1@example.com", "User One");
            eventService.registerParticipant(secondEvent.getId(), "user1@example.com", "User One");

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
        @DisplayName("rejects check-in at the deadline without changing attendance")
        void rejectsCheckinAtDeadline() {
            eventService.registerParticipant(persistedEvent.getId(), "user@example.com", "User Example");
            Subscription subscription = subscriptionRepository.findAll().get(0);
            persistedEvent.setDate(NOW);
            eventRepository.saveAndFlush(persistedEvent);

            assertThatThrownBy(() -> eventService.checkIn(subscription.getCheckinToken()))
                    .isInstanceOf(EventFinishedException.class);

            subscriptionRepository.flush();
            entityManager.clear();
            Subscription unchanged = subscriptionRepository.findById(subscription.getId()).orElseThrow();
            assertThat(unchanged.getCheckedInAt()).isNull();
        }

        @Test
        @DisplayName("marks the subscription as checked-in and persists the change")
        void marksSubscriptionCheckedInAndPersists() {
            eventService.registerParticipant(persistedEvent.getId(), "user@example.com", "User Example");

            Subscription subscription = subscriptionRepository.findAll().get(0);
            assertThat(subscription.isCheckedIn()).isFalse();

            eventService.checkIn(subscription.getCheckinToken());

            subscriptionRepository.flush();
            entityManager.clear();
            Subscription updated = subscriptionRepository.findById(subscription.getId()).orElseThrow();
            assertThat(updated.isCheckedIn()).isTrue();
            assertThat(updated.getCheckedInAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("is idempotent: checkedInAt does not change on second call")
        void isIdempotentOnSecondCall() {
            eventService.registerParticipant(persistedEvent.getId(), "user@example.com", "User Example");

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
