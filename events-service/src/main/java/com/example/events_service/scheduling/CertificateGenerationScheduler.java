package com.example.events_service.scheduling;

import com.example.events_service.domain.Event;
import com.example.events_service.domain.Subscription;
import com.example.events_service.messaging.event.CertificateGenerationRequestEvent;
import com.example.events_service.repository.EventRepository;
import com.example.events_service.repository.SubscriptionRepository;
import com.example.events_service.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CertificateGenerationScheduler {

    private static final Logger log = LoggerFactory.getLogger(CertificateGenerationScheduler.class);

    private static final String CERTIFICATE_GENERATION_ROUTING_KEY = "certificate.generation.requested";

    private final EventRepository eventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final OutboxService outboxService;

    @Scheduled(fixedDelayString = "${certificate.scheduler.delay-ms:60000}")
    @Transactional
    public void requestCertificatesForFinishedEvents() {

        Instant now = Instant.now();

        List<Event> finishedEvents = eventRepository.findFinishedEventsWithoutCertificateRequests(now);

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
            eventRepository.save(event);

            log.info("Certificate requests registered for event {}. Participants: {}", event.getId(), checkedIn.size());
        }
    }

}
