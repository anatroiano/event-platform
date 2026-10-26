package com.example.certificate_service.scheduling;

import com.example.certificate_service.service.OutboxDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxDeliveryService outboxDeliveryService;

    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:1000}")
    public void publishPending() {
        outboxDeliveryService.publishPending();
    }
}
