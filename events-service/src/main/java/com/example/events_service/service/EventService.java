package com.example.events_service.service;

import com.example.events_service.domain.Event;
import com.example.events_service.domain.Subscription;
import com.example.events_service.dto.CheckinInfoDTO;
import com.example.events_service.dto.EventRequestDTO;
import com.example.events_service.exception.AlreadySubscribedException;
import com.example.events_service.exception.EventFullException;
import com.example.events_service.exception.EventNotFoundException;
import com.example.events_service.exception.SubscriptionNotFoundException;
import com.example.events_service.messaging.event.EmailNotificationEvent;
import com.example.events_service.repository.EventRepository;
import com.example.events_service.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
@Transactional
@RequiredArgsConstructor
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private static final String SUBSCRIPTION_CREATED_ROUTING_KEY = "email.subscription.created";
    private static final String SUBSCRIPTION_CONFIRMATION_TEMPLATE = "subscription-confirmation";

    private final EventRepository eventRepository;

    private final SubscriptionRepository subscriptionRepository;

    private final OutboxService outboxService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public Event create(EventRequestDTO eventRequest) {

        log.info("Creating new event");

        Event event = Event.from(eventRequest);

        return eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public Page<Event> findAll(Pageable pageable) {

        log.info("Fetching events - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());

        return eventRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Event> findUpcoming(Pageable pageable) {

        log.info("Fetching upcoming events - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());

        return eventRepository.findUpcoming(Instant.now(), pageable);
    }

    public void registerParticipant(String eventId, String participantEmail) {

        log.info("Registering participant - eventId: {}, email: {}", eventId, participantEmail);

        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> {
                    log.warn("Cannot register participant - event not found: {}", eventId);
                    return new EventNotFoundException();
                });

        if (subscriptionRepository.existsByEventIdAndParticipantEmail(eventId, participantEmail)) {
            log.warn("Participant already subscribed - eventId: {}, email: {}", eventId, participantEmail);
            throw new AlreadySubscribedException();
        }

        if (isEventFull(event)) {
            log.warn("Cannot register participant - event is full - eventId: {}, capacity: {}", eventId, event.getMaxParticipants());
            throw new EventFullException();
        }

        Subscription subscription = Subscription.from(event, participantEmail);
        subscriptionRepository.save(subscription);

        sendSubscriptionConfirmation(event, subscription);
    }

    @Transactional(readOnly = true)
    public CheckinInfoDTO getCheckinInfo(String token) {

        log.debug("Fetching check-in information");

        Subscription subscription = subscriptionRepository.findByCheckinToken(token)
                .orElseThrow(() -> {
                    log.warn("Check-in information not found");
                    return new SubscriptionNotFoundException();
                });

        Event event = subscription.getEvent();

        return new CheckinInfoDTO(
                event.getTitle(),
                event.getDate(),
                subscription.isCheckedIn()
        );
    }

    public void checkIn(String token) {

        log.info("Confirming participant check-in");

        Subscription subscription = subscriptionRepository.findByCheckinToken(token)
                .orElseThrow(() -> {
                    log.warn("Cannot check-in - subscription not found");
                    return new SubscriptionNotFoundException();
                });

        if (subscription.isCheckedIn()) {
            log.info("Participant already checked in - subscriptionId: {}", subscription.getId());
            return;
        }

        subscription.checkIn();
    }

    private boolean isEventFull(Event event) {

        long registeredParticipants = subscriptionRepository.countByEventId(event.getId());

        return registeredParticipants >= event.getMaxParticipants();
    }

    private void sendSubscriptionConfirmation(Event event, Subscription subscription) {

        String checkinUrl = buildCheckinUrl(subscription);

        EmailNotificationEvent notification = EmailNotificationEvent.of(
                SUBSCRIPTION_CONFIRMATION_TEMPLATE,
                subscription.getParticipantEmail(),
                Map.of(
                        "eventTitle", event.getTitle(),
                        "subscriberEmail", subscription.getParticipantEmail(),
                        "checkinUrl", checkinUrl
                )
        );

        outboxService.save(SUBSCRIPTION_CREATED_ROUTING_KEY, notification);
    }

    private String buildCheckinUrl(Subscription subscription) {
        return frontendUrl + "/checkin/" + subscription.getCheckinToken();
    }
}