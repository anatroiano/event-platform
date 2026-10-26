package com.example.certificate_service.service;

import com.example.certificate_service.domain.OutboxMessage;
import com.example.certificate_service.messaging.event.CertificateEmailNotificationEvent;
import com.example.certificate_service.messaging.publisher.CertificateNotificationPublisher;
import com.example.certificate_service.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class OutboxDeliveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private CertificateNotificationPublisher publisher;

    private OutboxDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new OutboxDeliveryService(outboxRepository, publisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private OutboxMessage message() {
        return OutboxMessage.from(CertificateEmailNotificationEvent.of("user@example.com", Map.of(), NOW), "{}");
    }

    @Test
    void marksNotificationSentAfterSuccessfulPublication() {
        OutboxMessage message = message();
        given(outboxRepository.findPendingForUpdate(anyInt(), any())).willReturn(List.of(message));

        service.publishPending();

        assertThat(message.getProcessedAt()).isEqualTo(NOW);
        assertThat(message.getAttempts()).isZero();
    }

    @Test
    void retriesSameMessageAfterPublicationFailure() {
        OutboxMessage message = message();
        String originalId = message.getId();
        String originalPayload = message.getPayload();
        given(outboxRepository.findPendingForUpdate(anyInt(), any())).willReturn(List.of(message));
        willThrow(new IllegalStateException("Broker unavailable")).willDoNothing().given(publisher).publish(message);

        service.publishPending();

        assertThat(message.getProcessedAt()).isNull();
        assertThat(message.getAttempts()).isEqualTo(1);

        service.publishPending();

        assertThat(message.getProcessedAt()).isEqualTo(NOW);
        assertThat(message.getId()).isEqualTo(originalId);
        assertThat(message.getPayload()).isEqualTo(originalPayload);
        then(publisher).should(times(2)).publish(message);
    }

    @Test
    void continuesPublishingOtherNotificationsAfterAFailure() {
        OutboxMessage failed = message();
        OutboxMessage successful = message();
        given(outboxRepository.findPendingForUpdate(anyInt(), any())).willReturn(List.of(failed, successful));
        willThrow(new IllegalStateException("Rejected")).given(publisher).publish(failed);

        service.publishPending();

        assertThat(failed.getProcessedAt()).isNull();
        assertThat(successful.getProcessedAt()).isEqualTo(NOW);
    }
}
