package com.example.events_service.service;

import com.example.events_service.domain.Event;
import com.example.events_service.domain.Subscription;
import com.example.events_service.dto.CheckinInfoDTO;
import com.example.events_service.dto.EventRequestDTO;
import com.example.events_service.exception.AlreadySubscribedException;
import com.example.events_service.exception.EventFullException;
import com.example.events_service.exception.EventNotFoundException;
import com.example.events_service.exception.SubscriptionNotFoundException;
import com.example.events_service.repository.EventRepository;
import com.example.events_service.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private EventService eventService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(eventService, "frontendUrl", "http://localhost:3000");
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("should persist and return the created event")
        void shouldPersistAndReturnEvent() {
            EventRequestDTO dto = new EventRequestDTO(100, Instant.now().plusSeconds(3600), "Workshop", "Descrição");
            Event saved = Event.from(dto);

            when(eventRepository.save(any(Event.class))).thenReturn(saved);

            Event result = eventService.create(dto);

            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("Workshop");
            verify(eventRepository).save(any(Event.class));
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("should return the page from the repository")
        void shouldReturnPageFromRepository() {
            PageRequest pageable = PageRequest.of(0, 10);
            Page<Event> page = new PageImpl<>(List.of());

            when(eventRepository.findAll(pageable)).thenReturn(page);

            Page<Event> result = eventService.findAll(pageable);

            assertThat(result).isEqualTo(page);
            verify(eventRepository).findAll(pageable);
        }
    }

    @Nested
    @DisplayName("findUpcoming")
    class FindUpcoming {

        @Test
        @DisplayName("should pass the current instant to the repository")
        void shouldPassCurrentTimeToRepository() {
            PageRequest pageable = PageRequest.of(0, 5);
            Page<Event> page = new PageImpl<>(List.of());

            when(eventRepository.findUpcoming(any(Instant.class), eq(pageable))).thenReturn(page);

            Page<Event> result = eventService.findUpcoming(pageable);

            assertThat(result).isEqualTo(page);
            verify(eventRepository).findUpcoming(any(Instant.class), eq(pageable));
        }
    }

    @Nested
    @DisplayName("registerParticipant")
    class RegisterParticipant {

        @Test
        @DisplayName("should save the subscription and trigger a confirmation email")
        void shouldSaveSubscriptionAndSendEmail() {
            String eventId = "evt-1";
            String email = "user@example.com";

            Event event = buildEvent(100);
            when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));
            when(subscriptionRepository.existsByEventIdAndParticipantEmail(eventId, email)).thenReturn(false);
            when(subscriptionRepository.countByEventId(eventId)).thenReturn(99L);
            when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

            eventService.registerParticipant(eventId, email);

            verify(subscriptionRepository).save(any(Subscription.class));
            verify(outboxService).save(eq("email.subscription.created"), any());
        }

        @Test
        @DisplayName("should build the email payload with the correct data")
        void shouldBuildEmailPayload() {
            String eventId = "evt-1";
            String email = "user@example.com";

            Event event = buildEvent(100);
            when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));
            when(subscriptionRepository.existsByEventIdAndParticipantEmail(eventId, email)).thenReturn(false);
            when(subscriptionRepository.countByEventId(eventId)).thenReturn(0L);
            when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            eventService.registerParticipant(eventId, email);

            ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
            verify(outboxService).save(anyString(), payloadCaptor.capture());
            assertThat(payloadCaptor.getValue()).isNotNull();
        }

        @Test
        @DisplayName("should throw EventNotFoundException when the event does not exist")
        void shouldThrowEventNotFoundException_whenEventDoesNotExist() {
            when(eventRepository.findByIdForUpdate("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.registerParticipant("missing", "user@example.com"))
                    .isInstanceOf(EventNotFoundException.class);

            verifyNoInteractions(subscriptionRepository, outboxService);
        }

        @Test
        @DisplayName("should throw AlreadySubscribedException when participant is already registered")
        void shouldThrowAlreadySubscribedException_whenAlreadyRegistered() {
            String eventId = "evt-1";
            String email = "user@example.com";

            Event event = buildEvent(100);
            when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));
            when(subscriptionRepository.existsByEventIdAndParticipantEmail(eventId, email)).thenReturn(true);

            assertThatThrownBy(() -> eventService.registerParticipant(eventId, email))
                    .isInstanceOf(AlreadySubscribedException.class);

            verify(subscriptionRepository, never()).save(any());
            verifyNoInteractions(outboxService);
        }

        @Test
        @DisplayName("should throw EventFullException when the event has reached maximum capacity")
        void shouldThrowEventFullException_whenCapacityReached() {
            String eventId = "evt-1";
            String email = "user@example.com";

            Event event = buildEvent(50);
            when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));
            when(subscriptionRepository.existsByEventIdAndParticipantEmail(eventId, email)).thenReturn(false);
            when(subscriptionRepository.countByEventId(eventId)).thenReturn(50L);

            assertThatThrownBy(() -> eventService.registerParticipant(eventId, email))
                    .isInstanceOf(EventFullException.class);

            verify(subscriptionRepository, never()).save(any());
            verifyNoInteractions(outboxService);
        }
    }

    @Nested
    @DisplayName("getCheckinInfo")
    class GetCheckinInfo {

        @Test
        @DisplayName("should return the DTO with the correct information")
        void shouldReturnCorrectDTO() {
            Event event = buildEvent(10);
            Subscription subscription = Subscription.from(event, "user@example.com");

            when(subscriptionRepository.findByCheckinToken(subscription.getCheckinToken()))
                    .thenReturn(Optional.of(subscription));

            CheckinInfoDTO dto = eventService.getCheckinInfo(subscription.getCheckinToken());

            assertThat(dto.eventTitle()).isEqualTo("Evento Teste");
            assertThat(dto.alreadyCheckedIn()).isFalse();
        }

        @Test
        @DisplayName("should throw SubscriptionNotFoundException when the token does not exist")
        void shouldThrowSubscriptionNotFoundException_whenTokenNotFound() {
            when(subscriptionRepository.findByCheckinToken("invalid-token")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.getCheckinInfo("invalid-token"))
                    .isInstanceOf(SubscriptionNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("checkIn")
    class CheckIn {

        @Test
        @DisplayName("should mark the subscription as checked in")
        void shouldMarkSubscriptionAsCheckedIn() {
            Event event = buildEvent(10);
            Subscription subscription = Subscription.from(event, "user@example.com");

            when(subscriptionRepository.findByCheckinToken(subscription.getCheckinToken()))
                    .thenReturn(Optional.of(subscription));

            eventService.checkIn(subscription.getCheckinToken());

            assertThat(subscription.isCheckedIn()).isTrue();
        }

        @Test
        @DisplayName("should be idempotent when check-in has already been performed")
        void shouldBeIdempotent_whenAlreadyCheckedIn() {
            Event event = buildEvent(10);
            Subscription subscription = Subscription.from(event, "user@example.com");
            subscription.checkIn();

            when(subscriptionRepository.findByCheckinToken(subscription.getCheckinToken()))
                    .thenReturn(Optional.of(subscription));

            eventService.checkIn(subscription.getCheckinToken());

            assertThat(subscription.isCheckedIn()).isTrue();
        }

        @Test
        @DisplayName("should throw SubscriptionNotFoundException when the token does not exist")
        void shouldThrowSubscriptionNotFoundException_whenTokenNotFound() {
            when(subscriptionRepository.findByCheckinToken("bad-token")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.checkIn("bad-token"))
                    .isInstanceOf(SubscriptionNotFoundException.class);
        }
    }

    private Event buildEvent(int maxParticipants) {
        return Event.from(new EventRequestDTO(
                maxParticipants,
                Instant.now().plusSeconds(3600),
                "Evento Teste",
                "Descrição do evento"
        ));
    }
}
