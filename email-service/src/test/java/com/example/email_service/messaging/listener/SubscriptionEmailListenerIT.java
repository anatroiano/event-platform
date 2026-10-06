package com.example.email_service.messaging.listener;

import com.example.email_service.TestcontainersConfiguration;
import com.example.email_service.config.RabbitConfig;
import com.example.email_service.sender.EmailSender;
import com.example.email_service.service.ProcessedMessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@SpringBootTest
@Testcontainers
@Import(TestcontainersConfiguration.class)
@DisplayName("SubscriptionEmailListener — integration")
class SubscriptionEmailListenerIT {
    
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private EmailSender emailSender;

    @MockitoBean
    private ProcessedMessageService processedMessageService;

    @Nested
    @DisplayName("message consumption")
    class MessageConsumption {

        @Test
        @DisplayName("deserializes message from queue and dispatches email")
        void deserializesAndDispatches() {
            String messageId = UUID.randomUUID().toString();
            given(processedMessageService.tryClaim(messageId)).willReturn(true);

            String json = """
                    {
                      "messageId": "%s",
                      "correlationId": null,
                      "templateId": "subscription-confirmation",
                      "recipientEmail": "listener-test@example.com",
                      "templateData": {"eventTitle": "Test Event"},
                      "occurredAt": "2030-06-01T10:00:00Z"
                    }
                    """.formatted(messageId);

            Message message = MessageBuilder
                    .withBody(json.getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setMessageId(messageId)
                    .build();

            rabbitTemplate.send(RabbitConfig.NOTIFICATIONS_EXCHANGE, "email.subscription.created", message);

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    then(emailSender).should().send(
                            argThat(to -> to.equals("listener-test@example.com")),
                            any(),
                            any()
                    )
            );
        }
    }
}
