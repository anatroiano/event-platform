package com.example.events_service.messaging.publisher;

import com.example.events_service.domain.OutboxMessage;
import com.example.events_service.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 30;

    private final OutboxRepository outboxRepository;
    private final NotificationPublisher notificationPublisher;

    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:2000}")
    @Transactional
    public void publishPending() {

        List<OutboxMessage> batch = outboxRepository.findPendingForUpdate(PageRequest.of(0, BATCH_SIZE));

        if (batch.isEmpty()) return;
        log.info("Publishing {} pending outbox messages", batch.size());

        for (OutboxMessage message : batch) {
            try {
                notificationPublisher.publish(message.getRoutingKey(), message.getId(), message.getPayload());
                message.markAsSent();
            } catch (Exception e) {
                log.error("Failed to publish outbox message {}", message.getId(), e);
                message.registerFailure(MAX_ATTEMPTS);
            }
        }
    }
}