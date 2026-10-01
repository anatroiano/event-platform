package com.example.events_service.messaging.publisher;

import com.example.events_service.domain.OutboxMessage;
import com.example.events_service.enums.OutboxStatus;
import com.example.events_service.repository.OutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private NotificationPublisher notificationPublisher;

    @InjectMocks
    private OutboxRelay outboxRelay;

    @Nested
    @DisplayName("publishPending")
    class PublishPending {

        @Test
        @DisplayName("should not interact with the publisher when there are no pending messages")
        void shouldDoNothing_whenNoPendingMessages() {
            when(outboxRepository.findPendingForUpdate(any(Pageable.class))).thenReturn(List.of());

            outboxRelay.publishPending();

            verifyNoInteractions(notificationPublisher);
        }

        @Test
        @DisplayName("should mark the message as SENT when publishing succeeds")
        void shouldMarkMessageAsSent_whenPublishSucceeds() {
            OutboxMessage message = OutboxMessage.of("email.subscription.created", "{\"key\":\"value\"}");
            when(outboxRepository.findPendingForUpdate(any(Pageable.class))).thenReturn(List.of(message));

            outboxRelay.publishPending();

            verify(notificationPublisher).publish(message.getRoutingKey(), message.getId(), message.getPayload());
            assertThat(message.getStatus()).isEqualTo(OutboxStatus.SENT);
        }

        @Test
        @DisplayName("should register a failure when the publisher throws an exception")
        void shouldRegisterFailure_whenPublishThrows() {
            OutboxMessage message = OutboxMessage.of("email.subscription.created", "payload");
            when(outboxRepository.findPendingForUpdate(any(Pageable.class))).thenReturn(List.of(message));
            doThrow(new RuntimeException("RabbitMQ down"))
                    .when(notificationPublisher).publish(anyString(), anyString(), anyString());

            outboxRelay.publishPending();

            assertThat(message.getAttempts()).isEqualTo(1);
            assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
        }

        @Test
        @DisplayName("should continue processing remaining messages when one fails")
        void shouldContinueProcessing_whenOneMessageFails() {
            OutboxMessage failingMessage = OutboxMessage.of("routing.key", "payload1");
            OutboxMessage successMessage = OutboxMessage.of("routing.key", "payload2");

            when(outboxRepository.findPendingForUpdate(any(Pageable.class)))
                    .thenReturn(List.of(failingMessage, successMessage));

            doThrow(new RuntimeException("falha"))
                    .when(notificationPublisher).publish(anyString(), eq(failingMessage.getId()), anyString());

            outboxRelay.publishPending();

            assertThat(failingMessage.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(successMessage.getStatus()).isEqualTo(OutboxStatus.SENT);
        }
    }
}
