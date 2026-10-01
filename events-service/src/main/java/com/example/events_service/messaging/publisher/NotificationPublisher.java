package com.example.events_service.messaging.publisher;

import com.example.events_service.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(String routingKey, String messageId, String jsonPayload) {
        Message message = MessageBuilder
                .withBody(jsonPayload.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(messageId)
                .build();

        rabbitTemplate.send(RabbitConfig.NOTIFICATIONS_EXCHANGE, routingKey, message);
    }
}