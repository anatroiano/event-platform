package com.example.events_service.service;

import com.example.events_service.domain.Event;
import com.example.events_service.domain.Subscription;
import com.example.events_service.messaging.event.CertificateGenerationRequestEvent;
import com.example.events_service.repository.EventRepository;
import com.example.events_service.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class CertificateGenerationService {

    private static final Logger log = LoggerFactory.getLogger(CertificateGenerationService.class);

    private static final String CERTIFICATE_GENERATION_ROUTING_KEY = "certificate.generation.requested";

    private final EventRepository eventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final OutboxService outboxService;
    private final Clock clock;

    public void requestCertificatesForFinishedEvents() {

        List<Event> finishedEvents = eventRepository.findFinishedEventsWithoutCertificateRequests(clock.instant());

        if (finishedEvents.isEmpty()) {
            return;
        }

        for (Event event : finishedEvents) {
            List<Subscription> checkedIn = subscriptionRepository.findByEventIdAndCheckedInAtIsNotNull(event.getId());

            for (Subscription subscription : checkedIn) {

                CertificateGenerationRequestEvent requestEvent = CertificateGenerationRequestEvent.of(
                        event.getId(),
                        event.getTitle(),
                        String.valueOf(subscription.getId()),
                        subscription.getParticipantName(),
                        subscription.getParticipantEmail()
                );

                outboxService.save(CERTIFICATE_GENERATION_ROUTING_KEY, requestEvent);
            }

            event.setCertificateRequestsSent(true);

            log.info("Certificate requests registered for event {}. Participants: {}", event.getId(), checkedIn.size());
        }
    }

}
