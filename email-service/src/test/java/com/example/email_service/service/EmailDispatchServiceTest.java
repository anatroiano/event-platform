package com.example.email_service.service;

import com.example.email_service.messaging.event.EmailNotificationEvent;
import com.example.email_service.sender.EmailDeliveryException;
import com.example.email_service.sender.EmailSender;
import com.example.email_service.template.EmailTemplateResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailDispatchService")
class EmailDispatchServiceTest {

    @Mock
    private ProcessedMessageService processedMessageService;

    @Mock
    private EmailTemplateResolver templateResolver;

    @Mock
    private EmailSender emailSender;

    @InjectMocks
    private EmailDispatchService emailDispatchService;

    private EmailNotificationEvent buildEvent(String messageId) {
        return new EmailNotificationEvent(
                messageId,
                null,
                "subscription-confirmation",
                "user@example.com",
                Map.of("eventTitle", "Spring Boot Workshop"),
                Instant.parse("2026-01-01T12:00:00Z")
        );
    }

    @Nested
    @DisplayName("dispatch()")
    class Dispatch {

        @Test
        @DisplayName("skips dispatch when message was already processed")
        void skipsDispatchWhenMessageAlreadyClaimed() {
            EmailNotificationEvent event = buildEvent(UUID.randomUUID().toString());
            given(processedMessageService.tryClaim(event.messageId())).willReturn(false);

            emailDispatchService.dispatch(event);

            then(templateResolver).should(never()).resolveSubject(any());
            then(templateResolver).should(never()).render(any(), any());
            then(emailSender).should(never()).send(any(), any(), any());
        }

        @Test
        @DisplayName("resolves template, renders body and sends email on happy path")
        void sendsEmailOnHappyPath() {
            EmailNotificationEvent event = buildEvent(UUID.randomUUID().toString());

            given(processedMessageService.tryClaim(event.messageId())).willReturn(true);
            given(templateResolver.resolveSubject("subscription-confirmation")).willReturn("Confirmação de inscrição");
            given(templateResolver.render(anyString(), any())).willReturn("<html>body</html>");

            emailDispatchService.dispatch(event);

            then(templateResolver).should().resolveSubject("subscription-confirmation");
            then(templateResolver).should().render("subscription-confirmation", event.templateData());
            then(emailSender).should().send("user@example.com", "Confirmação de inscrição", "<html>body</html>");
        }

        @Test
        @DisplayName("propagates delivery failures so the transaction can roll back")
        void propagatesDeliveryFailure() {
            EmailNotificationEvent event = buildEvent(UUID.randomUUID().toString());
            EmailDeliveryException deliveryFailure = new EmailDeliveryException("SMTP error", new RuntimeException());

            given(processedMessageService.tryClaim(event.messageId())).willReturn(true);
            given(templateResolver.resolveSubject(any())).willReturn("Subject");
            given(templateResolver.render(any(), any())).willReturn("<html/>");
            willThrow(deliveryFailure).given(emailSender).send(any(), any(), any());

            assertThatThrownBy(() -> emailDispatchService.dispatch(event))
                    .isSameAs(deliveryFailure);

        }

        @Test
        @DisplayName("does not send email when template rendering fails")
        void propagatesRenderingFailure() {
            EmailNotificationEvent event = buildEvent(UUID.randomUUID().toString());
            RuntimeException failure = new RuntimeException("Template rendering failed");
            given(processedMessageService.tryClaim(event.messageId())).willReturn(true);
            given(templateResolver.resolveSubject(any())).willReturn("Subject");
            given(templateResolver.render(any(), any())).willThrow(failure);

            assertThatThrownBy(() -> emailDispatchService.dispatch(event)).isSameAs(failure);

            then(emailSender).shouldHaveNoInteractions();
        }
    }
}
