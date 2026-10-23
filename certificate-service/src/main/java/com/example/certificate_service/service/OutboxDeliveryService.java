package com.example.certificate_service.service;

import com.example.certificate_service.domain.OutboxMessage;
import com.example.certificate_service.messaging.publisher.CertificateNotificationPublisher;
import com.example.certificate_service.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@Transactional
@RequiredArgsConstructor
public class OutboxDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(OutboxDeliveryService.class);
    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 30;

    private final OutboxRepository outboxRepository;
    private final CertificateNotificationPublisher notificationPublisher;
    private final Clock clock;

    public void publishPending() {
        for (OutboxMessage message : outboxRepository.findPendingForUpdate(MAX_ATTEMPTS, PageRequest.of(0, BATCH_SIZE))) {
            try {
                notificationPublisher.publish(message);
                message.markAsSent(clock.instant());
            } catch (RuntimeException ex) {
                message.registerFailure();
                log.error("Failed to publish certificate notification: messageId={}, attempt={}",
                        message.getId(), message.getAttempts(), ex);
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
            }
        }
    }
}
