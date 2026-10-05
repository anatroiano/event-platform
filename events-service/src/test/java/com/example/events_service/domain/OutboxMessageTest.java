package com.example.events_service.domain;

import com.example.events_service.enums.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OutboxMessage")
class OutboxMessageTest {

    private static final int MAX_ATTEMPTS = 30;

    private OutboxMessage newPendingMessage() {
        return OutboxMessage.of("email.subscription.created", "{\"key\":\"value\"}");
    }

    @Nested
    @DisplayName("of()")
    class Of {

        @Test
        @DisplayName("creates a message with PENDING status and a generated id")
        void createsPendingMessageWithGeneratedId() {
            OutboxMessage message = newPendingMessage();

            assertThat(message.getId()).isNotBlank();
            assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(message.getAttempts()).isZero();
            assertThat(message.getProcessedAt()).isNull();
            assertThat(message.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("stores the provided routing key and payload")
        void storesRoutingKeyAndPayload() {
            OutboxMessage message = OutboxMessage.of("my.routing.key", "{\"foo\":\"bar\"}");

            assertThat(message.getRoutingKey()).isEqualTo("my.routing.key");
            assertThat(message.getPayload()).isEqualTo("{\"foo\":\"bar\"}");
        }

        @Test
        @DisplayName("generates a unique id for each message")
        void generatesUniqueIds() {
            OutboxMessage first = newPendingMessage();
            OutboxMessage second = newPendingMessage();

            assertThat(first.getId()).isNotEqualTo(second.getId());
        }
    }

    @Nested
    @DisplayName("markAsSent()")
    class MarkAsSent {

        @Test
        @DisplayName("transitions status to SENT")
        void transitionsStatusToSent() {
            OutboxMessage message = newPendingMessage();

            message.markAsSent();

            assertThat(message.getStatus()).isEqualTo(OutboxStatus.SENT);
        }

        @Test
        @DisplayName("sets processedAt to a non-null value")
        void setsProcessedAt() {
            OutboxMessage message = newPendingMessage();

            message.markAsSent();

            assertThat(message.getProcessedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("registerFailure()")
    class RegisterFailure {

        @Test
        @DisplayName("increments the attempts counter")
        void incrementsAttempts() {
            OutboxMessage message = newPendingMessage();

            message.registerFailure(MAX_ATTEMPTS);

            assertThat(message.getAttempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("keeps status as PENDING while below the max attempts limit")
        void keepsPendingWhileBelowMaxAttempts() {
            OutboxMessage message = newPendingMessage();

            for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
                message.registerFailure(MAX_ATTEMPTS);
            }

            assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(message.getAttempts()).isEqualTo(MAX_ATTEMPTS - 1);
        }

        @Test
        @DisplayName("transitions status to FAILED when max attempts is reached")
        void transitionsToFailedAtMaxAttempts() {
            OutboxMessage message = newPendingMessage();

            for (int i = 0; i < MAX_ATTEMPTS; i++) {
                message.registerFailure(MAX_ATTEMPTS);
            }

            assertThat(message.getStatus()).isEqualTo(OutboxStatus.FAILED);
        }
    }
}
