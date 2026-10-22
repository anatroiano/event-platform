package com.example.email_service.messaging.listener;

import com.example.email_service.messaging.event.EmailNotificationEvent;
import com.example.email_service.service.EmailDispatchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willAnswer;

@ExtendWith(MockitoExtension.class)
class SubscriptionEmailListenerTest {

    @Mock
    private EmailDispatchService emailDispatchService;

    @InjectMocks
    private SubscriptionEmailListener listener;

    @AfterEach
    void clearContext() {
        MDC.clear();
    }

    private EmailNotificationEvent buildEvent(String correlationId) {
        return new EmailNotificationEvent("msg-listener", correlationId, "subscription-confirmation",
                "user@example.com", Map.of(), Instant.parse("2026-01-01T12:00:00Z"));
    }

    @Test
    void restoresPreviousContextAfterDispatch() {
        MDC.setContextMap(Map.of("traceId", "existing-trace", "messageId", "previous-message",
                "correlationId", "previous-correlation"));
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        EmailNotificationEvent event = buildEvent("current-correlation");
        willAnswer(invocation -> {
            assertThat(MDC.get("messageId")).isEqualTo(event.messageId());
            assertThat(MDC.get("correlationId")).isEqualTo(event.correlationId());
            assertThat(MDC.get("traceId")).isEqualTo("existing-trace");
            return null;
        }).given(emailDispatchService).dispatch(event);

        listener.handle(event);

        assertThat(MDC.getCopyOfContextMap()).isEqualTo(previousContext);
    }

    @Test
    void clearsInheritedCorrelationAndRestoresContextOnFailure() {
        MDC.put("correlationId", "previous-correlation");
        EmailNotificationEvent event = buildEvent(null);
        RuntimeException failure = new RuntimeException("Dispatch failed");
        willAnswer(invocation -> {
            assertThat(MDC.get("correlationId")).isNull();
            throw failure;
        }).given(emailDispatchService).dispatch(event);

        assertThatThrownBy(() -> listener.handle(event)).isSameAs(failure);

        assertThat(MDC.get("correlationId")).isEqualTo("previous-correlation");
        assertThat(MDC.get("messageId")).isNull();
    }

    @Test
    void removesDeliveryContextWhenNoPreviousContextExists() {
        MDC.clear();
        EmailNotificationEvent event = buildEvent(null);

        listener.handle(event);

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
