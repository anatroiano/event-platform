package com.example.email_service.messaging.listener;

import com.example.email_service.config.RabbitConfig;
import com.example.email_service.messaging.event.EmailNotificationEvent;
import com.example.email_service.service.EmailDispatchService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubscriptionEmailListener {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEmailListener.class);

    private final EmailDispatchService emailDispatchService;

    @RabbitListener(queues = RabbitConfig.EMAIL_QUEUE)
    public void handle(EmailNotificationEvent event) {
        MDC.put("messageId", event.messageId());
        if (event.correlationId() != null) {
            MDC.put("correlationId", event.correlationId());
        }
        try {
            log.info("Notification event received: messageId={}, template={}",
                    event.messageId(), event.templateId());

            emailDispatchService.dispatch(event);

        } finally {
            MDC.clear();
        }
    }
}