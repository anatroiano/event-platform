package com.example.events_service.domain;

import com.example.events_service.enums.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxMessageTest {

    @Nested
    @DisplayName("of")
    class Of {

        @Test
        @DisplayName("should create a message with PENDING status and populated fields")
        void shouldCreateMessageWithPendingStatus() {
            OutboxMessage message = OutboxMessage.of("email.subscription.created", "{\"key\":\"value\"}");

            assertThat(message.getId()).isNotBlank();
            assertThat(message.getRoutingKey()).isEqualTo("email.subscription.created");
            assertThat(message.getPayload()).isEqualTo("{\"key\":\"value\"}");
            assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(message.getAttempts()).isZero();
            assertThat(message.getCreatedAt()).isNotNull();
            assertThat(message.getProcessedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("markAsSent")
    class MarkAsSent {

        @Test
        @DisplayName("should change status to SENT and set processedAt")
        void shouldTransitionStatusToSentAndSetProcessedAt() {
            OutboxMessage message = OutboxMessage.of("routing.key", "payload");

            message.markAsSent();

            assertThat(message.getStatus()).isEqualTo(OutboxStatus.SENT);
            assertThat(message.getProcessedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("registerFailure")
    class RegisterFailure {

        @Test
        @DisplayName("should increment attempts and keep PENDING status when below the limit")
        void shouldIncrementAttempts_whenBelowMaxAttempts() {
            OutboxMessage message = OutboxMessage.of("routing.key", "payload");

            message.registerFailure(3);

            assertThat(message.getAttempts()).isEqualTo(1);
            assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
        }

        @Test
        @DisplayName("should change status to FAILED when the max attempts limit is reached")
        void shouldMarkAsFailed_whenMaxAttemptsReached() {
            OutboxMessage message = OutboxMessage.of("routing.key", "payload");

            message.registerFailure(1);

            assertThat(message.getAttempts()).isEqualTo(1);
            assertThat(message.getStatus()).isEqualTo(OutboxStatus.FAILED);
        }

        @Test
        @DisplayName("should mark as FAILED only after exhausting all attempts")
        void shouldMarkAsFailedOnlyAfterReachingLimit() {
            OutboxMessage message = OutboxMessage.of("routing.key", "payload");

            message.registerFailure(3);
            assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);

            message.registerFailure(3);
            assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);

            message.registerFailure(3);
            assertThat(message.getStatus()).isEqualTo(OutboxStatus.FAILED);
            assertThat(message.getAttempts()).isEqualTo(3);
        }
    }
}
