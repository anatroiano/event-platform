package com.example.events_service.messaging.publisher;

import com.example.events_service.domain.OutboxMessage;
import com.example.events_service.repository.OutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelay")
class OutboxRelayTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private NotificationPublisher notificationPublisher;

    @InjectMocks
    private OutboxRelay outboxRelay;

    private OutboxMessage buildPendingMessage(String id, String routingKey) {
        OutboxMessage message = OutboxMessage.of(routingKey, "{\"key\":\"value\"}");
        ReflectionTestUtils.setField(message, "id", id);
        return message;
    }

    @Nested
    @DisplayName("publishPending()")
    class PublishPending {

        @Test
        @DisplayName("does nothing when there are no pending messages")
        void doesNothingWhenNoPendingMessages() {
            given(outboxRepository.findPendingForUpdate(any(PageRequest.class))).willReturn(List.of());

            outboxRelay.publishPending();

            then(notificationPublisher).should(never()).publish(any(), any(), any());
        }

        @Test
        @DisplayName("publishes all pending messages and marks them as sent")
        void publishesAllPendingMessagesAndMarksAsSent() {
            OutboxMessage message1 = buildPendingMessage("id-1", "email.subscription.created");
            OutboxMessage message2 = buildPendingMessage("id-2", "email.subscription.created");

            given(outboxRepository.findPendingForUpdate(any(PageRequest.class)))
                    .willReturn(List.of(message1, message2));

            outboxRelay.publishPending();

            then(notificationPublisher).should().publish("email.subscription.created", "id-1", message1.getPayload());
            then(notificationPublisher).should().publish("email.subscription.created", "id-2", message2.getPayload());

            assertThat(message1.getStatus()).isEqualTo(com.example.events_service.enums.OutboxStatus.SENT);
            assertThat(message2.getStatus()).isEqualTo(com.example.events_service.enums.OutboxStatus.SENT);
        }

        @Test
        @DisplayName("registers failure on a message when publishing throws an exception")
        void registersFailureWhenPublishingThrows() {
            OutboxMessage message = buildPendingMessage("id-1", "email.subscription.created");

            given(outboxRepository.findPendingForUpdate(any(PageRequest.class))).willReturn(List.of(message));
            willThrow(new RuntimeException("Broker unavailable"))
                    .given(notificationPublisher).publish(any(), any(), any());

            outboxRelay.publishPending();

            assertThat(message.getAttempts()).isEqualTo(1);
            assertThat(message.getStatus()).isEqualTo(com.example.events_service.enums.OutboxStatus.PENDING);
        }

        @Test
        @DisplayName("continues processing remaining messages after one fails")
        void continuesAfterSingleMessageFailure() {
            OutboxMessage failing = buildPendingMessage("id-1", "email.subscription.created");
            OutboxMessage succeeding = buildPendingMessage("id-2", "email.subscription.created");
            String failingPayload = failing.getPayload();

            given(outboxRepository.findPendingForUpdate(any(PageRequest.class)))
                    .willReturn(List.of(failing, succeeding));

            willThrow(new RuntimeException("Broker unavailable"))
                    .given(notificationPublisher).publish(any(), eq("id-1"), eq(failingPayload));

            outboxRelay.publishPending();

            assertThat(failing.getAttempts()).isEqualTo(1);
            assertThat(succeeding.getStatus()).isEqualTo(com.example.events_service.enums.OutboxStatus.SENT);
        }
    }
}
