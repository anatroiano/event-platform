package com.example.certificate_service.service;

import com.example.certificate_service.TestcontainersConfiguration;
import com.example.certificate_service.domain.OutboxMessage;
import com.example.certificate_service.messaging.event.CertificateEmailNotificationEvent;
import com.example.certificate_service.messaging.publisher.CertificateNotificationPublisher;
import com.example.certificate_service.repository.OutboxRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({OutboxDeliveryService.class, OutboxDeliveryServiceIT.TestConfig.class, TestcontainersConfiguration.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxDeliveryServiceIT {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @TestConfiguration
    static class TestConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private OutboxDeliveryService service;
    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private CertificateNotificationPublisher publisher;

    private OutboxMessage pendingMessage() {
        return outboxRepository.save(OutboxMessage.from(
                CertificateEmailNotificationEvent.of("user@example.com", Map.of(), NOW), "{}"));
    }

    @Test
    void persistsDeliveryAndDoesNotPublishItAgain() {
        String id = pendingMessage().getId();

        service.publishPending();
        entityManager.flush();
        entityManager.clear();

        assertThat(outboxRepository.findById(id).orElseThrow().getProcessedAt()).isEqualTo(NOW);

        service.publishPending();

        then(publisher).should().publish(any());
    }

    @Test
    void persistsFailedAttemptAndRetriesSameNotification() {
        String id = pendingMessage().getId();
        willThrow(new IllegalStateException("Broker unavailable")).willDoNothing().given(publisher).publish(any());

        service.publishPending();
        entityManager.flush();
        entityManager.clear();

        OutboxMessage failed = outboxRepository.findById(id).orElseThrow();
        assertThat(failed.getProcessedAt()).isNull();
        assertThat(failed.getAttempts()).isEqualTo(1);

        service.publishPending();
        entityManager.flush();
        entityManager.clear();

        OutboxMessage sent = outboxRepository.findById(id).orElseThrow();
        assertThat(sent.getProcessedAt()).isEqualTo(NOW);
        assertThat(sent.getPayload()).isEqualTo("{}");
        assertThat(outboxRepository.count()).isEqualTo(1);
    }
}
