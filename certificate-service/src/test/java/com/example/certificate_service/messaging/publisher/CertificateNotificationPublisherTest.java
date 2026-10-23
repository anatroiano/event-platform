package com.example.certificate_service.messaging.publisher;

import com.example.certificate_service.config.RabbitConfig;
import com.example.certificate_service.domain.OutboxMessage;
import com.example.certificate_service.messaging.event.CertificateEmailNotificationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;

@ExtendWith(MockitoExtension.class)
class CertificateNotificationPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private CertificateNotificationPublisher publisher;

    private OutboxMessage message() {
        return OutboxMessage.from(CertificateEmailNotificationEvent.of("user@example.com", Map.of(),
                Instant.parse("2026-01-01T12:00:00Z")), "{\"templateId\":\"certificate\"}");
    }

    @Test
    void publishesPersistedPayloadAndIdAndWaitsForConfirmation() {
        OutboxMessage message = message();
        willAnswer(invocation -> {
            invocation.getArgument(3, CorrelationData.class).getFuture()
                    .complete(new CorrelationData.Confirm(true, null));
            return null;
        }).given(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        publisher.publish(message);

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        then(rabbitTemplate).should().send(eq(RabbitConfig.NOTIFICATIONS_EXCHANGE),
                eq("email.certificate.generated"), sent.capture(), any(CorrelationData.class));
        assertThat(sent.getValue().getMessageProperties().getMessageId()).isEqualTo(message.getId());
        assertThat(new String(sent.getValue().getBody(), StandardCharsets.UTF_8)).isEqualTo(message.getPayload());
    }

    @Test
    void failsWhenBrokerRejectsPublication() {
        willAnswer(invocation -> {
            invocation.getArgument(3, CorrelationData.class).getFuture()
                    .complete(new CorrelationData.Confirm(false, "Rejected"));
            return null;
        }).given(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publish(message())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsWhenPublicationCannotBeRouted() {
        willAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3, CorrelationData.class);
            correlation.setReturned(new ReturnedMessage(invocation.getArgument(2, Message.class),
                    312, "NO_ROUTE", RabbitConfig.NOTIFICATIONS_EXCHANGE, "email.certificate.generated"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).given(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publish(message())).isInstanceOf(IllegalStateException.class);
    }
}
