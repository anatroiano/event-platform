package com.example.email_service.config;

import com.example.email_service.template.UnknownTemplateException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class RabbitConfig {

    public static final String NOTIFICATIONS_EXCHANGE = "notifications.exchange";
    public static final String EMAIL_QUEUE = "email.notifications.queue";
    public static final String EMAIL_ROUTING_PATTERN = "email.#";

    public static final String DLX = "notifications.exchange.dlx";
    public static final String EMAIL_DLQ = "email.notifications.dlq";

    @Bean
    public TopicExchange notificationsExchange() {
        return new TopicExchange(NOTIFICATIONS_EXCHANGE, true, false);
    }

    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(EMAIL_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", EMAIL_DLQ)
                .build();
    }

    @Bean
    public Binding emailBinding(Queue emailQueue, TopicExchange notificationsExchange) {
        return BindingBuilder.bind(emailQueue).to(notificationsExchange).with(EMAIL_ROUTING_PATTERN);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX);
    }

    @Bean
    public Queue emailDeadLetterQueue() {
        return QueueBuilder.durable(EMAIL_DLQ).build();
    }

    @Bean
    public Binding dlqBinding(Queue emailDeadLetterQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(emailDeadLetterQueue).to(dlxExchange).with(EMAIL_DLQ);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitListenerRetrySettingsCustomizer emailRetrySettingsCustomizer() {
        return settings -> settings.setExceptionExcludes(List.of(
                UnknownTemplateException.class,
                IllegalArgumentException.class,
                AmqpRejectAndDontRequeueException.class
        ));
    }
}