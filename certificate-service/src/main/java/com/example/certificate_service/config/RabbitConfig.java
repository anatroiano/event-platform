package com.example.certificate_service.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.validation.Validator;

import java.util.List;

@Configuration
public class RabbitConfig {

    public static final String NOTIFICATIONS_EXCHANGE = "notifications.exchange";
    public static final String CERTIFICATE_QUEUE = "certificate.generation.queue";
    public static final String CERTIFICATE_ROUTING_KEY = "certificate.generation.requested";

    public static final String DLX = "notifications.exchange.dlx";
    public static final String CERTIFICATE_DLQ = "certificate.generation.dlq";

    @Bean
    public TopicExchange notificationsExchange() {
        return new TopicExchange(NOTIFICATIONS_EXCHANGE, true, false);
    }

    @Bean
    public Queue certificateQueue() {
        return QueueBuilder.durable(CERTIFICATE_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", CERTIFICATE_DLQ)
                .build();
    }

    @Bean
    public Binding certificateBinding(Queue certificateQueue, TopicExchange notificationsExchange) {
        return BindingBuilder.bind(certificateQueue).to(notificationsExchange).with(CERTIFICATE_ROUTING_KEY);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX);
    }

    @Bean
    public Queue certificateDeadLetterQueue() {
        return QueueBuilder.durable(CERTIFICATE_DLQ).build();
    }

    @Bean
    public Binding certificateDlqBinding(Queue certificateDeadLetterQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(certificateDeadLetterQueue).to(dlxExchange).with(CERTIFICATE_DLQ);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitListenerConfigurer rabbitListenerConfigurer(Validator validator) {
        return registrar -> registrar.setValidator(validator);
    }

    @Bean
    public RabbitListenerRetrySettingsCustomizer certificateRetrySettingsCustomizer() {
        return settings -> settings.setExceptionExcludes(List.of(
                MethodArgumentNotValidException.class,
                IllegalArgumentException.class,
                AmqpRejectAndDontRequeueException.class
        ));
    }
}
