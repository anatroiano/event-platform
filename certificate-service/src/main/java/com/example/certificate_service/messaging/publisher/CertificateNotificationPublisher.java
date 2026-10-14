package com.example.certificate_service.messaging.publisher;

import com.example.certificate_service.config.RabbitConfig;
import com.example.certificate_service.messaging.event.CertificateEmailNotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class CertificateNotificationPublisher {

    private static final String EMAIL_ROUTING_KEY = "email.certificate.generated";

    private final RabbitTemplate rabbitTemplate;

    private final JsonMapper jsonMapper;

    public void publish(CertificateEmailNotificationEvent event) {
        String jsonPayload = jsonMapper.writeValueAsString(event);

        Message message = MessageBuilder
                .withBody(jsonPayload.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(event.messageId())
                .build();
        rabbitTemplate.send(RabbitConfig.NOTIFICATIONS_EXCHANGE, EMAIL_ROUTING_KEY, message);
    }
}