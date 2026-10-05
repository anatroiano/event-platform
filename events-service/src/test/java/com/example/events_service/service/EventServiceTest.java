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
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventService")
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
    void injectFrontendUrl() {
        ReflectionTestUtils.setField(eventService, "frontendUrl", "https://app.example.com");
    }

    private Event buildEvent(String id, int maxParticipants) {
        EventRequestDTO dto = new EventRequestDTO(
                maxParticipants,
                Instant.parse("2030-06-01T10:00:00Z"),
                "Spring Boot Workshop",
                "A hands-on workshop"
        );
        Event event = Event.from(dto);
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    private Subscription buildSubscription(Event event, String email) {
        Subscription subscription = Subscription.from(event, email);
        ReflectionTestUtils.setField(subscription, "id", 1L);
        return subscription;
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("saves and returns the persisted event")
        void savesAndReturnsPersistedEvent() {
            EventRequestDTO request = new EventRequestDTO(
                    50,
                    Instant.parse("2030-06-01T10:00:00Z"),
                    "Spring Boot Workshop",
                    "A hands-on workshop"
            );
            Event saved = Event.from(request);
            ReflectionTestUtils.setField(saved, "id", "evt-123");
            given(eventRepository.save(any(Event.class))).willReturn(saved);

            Event result = eventService.create(request);

            assertThat(result.getId()).isEqualTo("evt-123");
            then(eventRepository).should().save(any(Event.class));
        }
    }

    @Nested
    @DisplayName("registerParticipant()")
    class RegisterParticipant {

        private static final String EVENT_ID = "evt-abc";
        private static final String EMAIL = "user@example.com";

        @Test
        @DisplayName("throws EventNotFoundException when event does not exist")
        void throwsWhenEventNotFound() {
            given(eventRepository.findByIdForUpdate(EVENT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.registerParticipant(EVENT_ID, EMAIL))
                    .isInstanceOf(EventNotFoundException.class);

            then(subscriptionRepository).should(never()).save(any());
            then(outboxService).should(never()).save(any(), any());
        }

        @Test
        @DisplayName("throws AlreadySubscribedException when participant is already registered")
        void throwsWhenParticipantAlreadySubscribed() {
            Event event = buildEvent(EVENT_ID, 100);
            given(eventRepository.findByIdForUpdate(EVENT_ID)).willReturn(Optional.of(event));
            given(subscriptionRepository.existsByEventIdAndParticipantEmail(EVENT_ID, EMAIL)).willReturn(true);

            assertThatThrownBy(() -> eventService.registerParticipant(EVENT_ID, EMAIL))
                    .isInstanceOf(AlreadySubscribedException.class);

            then(subscriptionRepository).should(never()).save(any());
            then(outboxService).should(never()).save(any(), any());
        }

        @Test
        @DisplayName("throws EventFullException when event has reached its maximum capacity")
        void throwsWhenEventIsFull() {
            int maxParticipants = 50;
            Event event = buildEvent(EVENT_ID, maxParticipants);

            given(eventRepository.findByIdForUpdate(EVENT_ID)).willReturn(Optional.of(event));
            given(subscriptionRepository.existsByEventIdAndParticipantEmail(EVENT_ID, EMAIL)).willReturn(false);
            given(subscriptionRepository.countByEventId(EVENT_ID)).willReturn((long) maxParticipants);

            assertThatThrownBy(() -> eventService.registerParticipant(EVENT_ID, EMAIL))
                    .isInstanceOf(EventFullException.class);

            then(subscriptionRepository).should(never()).save(any());
            then(outboxService).should(never()).save(any(), any());
        }

        @Test
        @DisplayName("saves subscription and queues outbox notification on happy path")
        void savesSubscriptionAndQueuesOutbox() {
            Event event = buildEvent(EVENT_ID, 100);

            given(eventRepository.findByIdForUpdate(EVENT_ID)).willReturn(Optional.of(event));
            given(subscriptionRepository.existsByEventIdAndParticipantEmail(EVENT_ID, EMAIL)).willReturn(false);
            given(subscriptionRepository.countByEventId(EVENT_ID)).willReturn(0L);

            eventService.registerParticipant(EVENT_ID, EMAIL);

            ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
            then(subscriptionRepository).should().save(subscriptionCaptor.capture());
            assertThat(subscriptionCaptor.getValue().getParticipantEmail()).isEqualTo(EMAIL);

            then(outboxService).should().save(eq("email.subscription.created"), any());
        }

        @Test
        @DisplayName("outbox notification contains the correct checkin URL")
        void outboxNotificationContainsCorrectCheckinUrl() {
            Event event = buildEvent(EVENT_ID, 100);

            given(eventRepository.findByIdForUpdate(EVENT_ID)).willReturn(Optional.of(event));
            given(subscriptionRepository.existsByEventIdAndParticipantEmail(EVENT_ID, EMAIL)).willReturn(false);
            given(subscriptionRepository.countByEventId(EVENT_ID)).willReturn(0L);

            eventService.registerParticipant(EVENT_ID, EMAIL);

            ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
            then(outboxService).should().save(any(), payloadCaptor.capture());

            var notification = (com.example.events_service.messaging.event.EmailNotificationEvent) payloadCaptor.getValue();
            assertThat(notification.templateData()).containsKey("checkinUrl");
            assertThat(notification.templateData().get("checkinUrl").toString())
                    .startsWith("https://app.example.com/checkin/");
        }
    }

    @Nested
    @DisplayName("getCheckinInfo()")
    class GetCheckinInfo {

        @Test
        @DisplayName("throws SubscriptionNotFoundException when token does not exist")
        void throwsWhenTokenNotFound() {
            given(subscriptionRepository.findByCheckinToken("unknown-token")).willReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.getCheckinInfo("unknown-token"))
                    .isInstanceOf(SubscriptionNotFoundException.class);
        }

        @Test
        @DisplayName("returns correct check-in info for a valid token")
        void returnsCheckinInfoForValidToken() {
            Event event = buildEvent("evt-xyz", 100);
            Subscription subscription = buildSubscription(event, "user@example.com");

            given(subscriptionRepository.findByCheckinToken("valid-token")).willReturn(Optional.of(subscription));

            CheckinInfoDTO result = eventService.getCheckinInfo("valid-token");

            assertThat(result.eventTitle()).isEqualTo("Spring Boot Workshop");
            assertThat(result.alreadyCheckedIn()).isFalse();
        }

        @Test
        @DisplayName("returns alreadyCheckedIn=true for a subscription that already checked in")
        void returnsAlreadyCheckedInTrue() {
            Event event = buildEvent("evt-xyz", 100);
            Subscription subscription = buildSubscription(event, "user@example.com");
            subscription.checkIn();

            given(subscriptionRepository.findByCheckinToken("valid-token")).willReturn(Optional.of(subscription));

            CheckinInfoDTO result = eventService.getCheckinInfo("valid-token");

            assertThat(result.alreadyCheckedIn()).isTrue();
        }
    }

    @Nested
    @DisplayName("checkIn()")
    class CheckIn {

        @Test
        @DisplayName("throws SubscriptionNotFoundException when token does not exist")
        void throwsWhenTokenNotFound() {
            given(subscriptionRepository.findByCheckinToken("bad-token")).willReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.checkIn("bad-token"))
                    .isInstanceOf(SubscriptionNotFoundException.class);
        }

        @Test
        @DisplayName("marks subscription as checked-in when not yet done")
        void marksSubscriptionAsCheckedIn() {
            Event event = buildEvent("evt-xyz", 100);
            Subscription subscription = buildSubscription(event, "user@example.com");
            assertThat(subscription.isCheckedIn()).isFalse();

            given(subscriptionRepository.findByCheckinToken("my-token")).willReturn(Optional.of(subscription));

            eventService.checkIn("my-token");

            assertThat(subscription.isCheckedIn()).isTrue();
        }

        @Test
        @DisplayName("is idempotent: does not re-check-in an already checked-in participant")
        void isIdempotentWhenAlreadyCheckedIn() {
            Event event = buildEvent("evt-xyz", 100);
            Subscription subscription = buildSubscription(event, "user@example.com");
            subscription.checkIn();
            Instant firstCheckinTime = subscription.getCheckedInAt();

            given(subscriptionRepository.findByCheckinToken("my-token")).willReturn(Optional.of(subscription));

            eventService.checkIn("my-token");

            assertThat(subscription.getCheckedInAt()).isEqualTo(firstCheckinTime);
        }
    }

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("delegates to repository and returns the page")
        void delegatesToRepositoryAndReturnsPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Event> page = new PageImpl<>(List.of(buildEvent("e1", 50)));
            given(eventRepository.findAll(pageable)).willReturn(page);

            Page<Event> result = eventService.findAll(pageable);

            assertThat(result).isSameAs(page);
        }
    }

    @Nested
    @DisplayName("findUpcoming()")
    class FindUpcoming {

        @Test
        @DisplayName("delegates to repository with current time and returns the page")
        void delegatesToRepositoryAndReturnsPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Event> page = new PageImpl<>(List.of(buildEvent("e1", 50)));
            given(eventRepository.findUpcoming(any(Instant.class), eq(pageable))).willReturn(page);

            Page<Event> result = eventService.findUpcoming(pageable);

            assertThat(result).isSameAs(page);
        }
    }
}
