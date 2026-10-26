package com.example.email_service.messaging.listener;

import com.example.email_service.TestcontainersConfiguration;
import com.example.email_service.config.RabbitConfig;
import com.example.email_service.repository.ProcessedMessageRepository;
import com.example.email_service.sender.EmailSender;
import org.junit.jupiter.api.AfterEach;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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

    @Autowired
    private ProcessedMessageRepository processedMessageRepository;

    @AfterEach
    void cleanUp() {
        processedMessageRepository.deleteAll();
    }

    @Nested
    @DisplayName("message consumption")
    class MessageConsumption {

        @Test
        @DisplayName("deserializes message from queue and dispatches email")
        void deserializesAndDispatches() {
            String messageId = UUID.randomUUID().toString();

            String json = """
                    {
                      "messageId": "%s",
                      "correlationId": null,
                      "templateId": "subscription-confirmation",
                      "recipientEmail": "listener-test@example.com",
                      "templateData": {
                        "eventTitle": "Test Event",
                        "checkinUrl": "https://app.example.com/checkin/token"
                      },
                      "occurredAt": "2030-06-01T10:00:00Z"
                    }
                    """.formatted(messageId);

            Message message = MessageBuilder
                    .withBody(json.getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setMessageId(messageId)
                    .build();

            rabbitTemplate.send(RabbitConfig.NOTIFICATIONS_EXCHANGE, "email.subscription.created", message);

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                then(emailSender).should().send(
                        argThat(to -> to.equals("listener-test@example.com")),
                        any(),
                        any()
                );
                assertThat(processedMessageRepository.findAll())
                        .anyMatch(messageRecord -> messageRecord.getMessageId().equals(messageId));
            });
        }

        @Test
        @DisplayName("rejects invalid recipients to the dead-letter queue without claiming or sending")
        void rejectsInvalidRecipient() {
            String messageId = UUID.randomUUID().toString();
            String json = """
                    {
                      "messageId": "%s",
                      "templateId": "subscription-confirmation",
                      "recipientEmail": "invalid-email"
                    }
                    """.formatted(messageId);
            Message invalidMessage = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setMessageId(messageId)
                    .build();

            rabbitTemplate.send(RabbitConfig.NOTIFICATIONS_EXCHANGE, "email.subscription.created", invalidMessage);

            AtomicReference<Message> rejected = new AtomicReference<>();
            await().atMost(10, TimeUnit.SECONDS).until(() -> {
                rejected.set(rabbitTemplate.receive(RabbitConfig.EMAIL_DLQ));
                return rejected.get() != null;
            });

            assertThat(new String(rejected.get().getBody(), StandardCharsets.UTF_8)).contains(messageId);
            then(emailSender).shouldHaveNoInteractions();
            assertThat(processedMessageRepository.findAll())
                    .noneMatch(messageRecord -> messageRecord.getMessageId().equals(messageId));
        }
    }
}
