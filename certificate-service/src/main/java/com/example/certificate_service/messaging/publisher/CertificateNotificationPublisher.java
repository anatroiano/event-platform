package com.example.certificate_service.messaging.publisher;

import com.example.certificate_service.config.RabbitConfig;
import com.example.certificate_service.domain.OutboxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
public class CertificateNotificationPublisher {

    private static final String EMAIL_ROUTING_KEY = "email.certificate.generated";

    private final RabbitTemplate rabbitTemplate;

    public void publish(OutboxMessage outboxMessage) {
        Message message = MessageBuilder
                .withBody(outboxMessage.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(outboxMessage.getId())
                .build();
        CorrelationData correlation = new CorrelationData(outboxMessage.getId());
        rabbitTemplate.send(RabbitConfig.NOTIFICATIONS_EXCHANGE, EMAIL_ROUTING_KEY, message, correlation);

        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
            if (!confirm.ack() || correlation.getReturned() != null) {
                throw new IllegalStateException("Certificate notification was not accepted or routed by RabbitMQ");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Certificate notification publication was interrupted", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("Failed to confirm certificate notification publication", ex);
        }
    }
}
