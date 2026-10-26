package com.example.email_service.service;

import com.example.email_service.messaging.event.EmailNotificationEvent;
import com.example.email_service.repository.ProcessedMessageRepository;
import com.example.email_service.sender.EmailDeliveryException;
import com.example.email_service.sender.EmailSender;
import com.example.email_service.template.EmailTemplateResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
@Import({EmailDispatchService.class, ProcessedMessageService.class, EmailDispatchServiceIT.TestConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmailDispatchServiceIT {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @TestConfiguration
    static class TestConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private EmailDispatchService emailDispatchService;

    @Autowired
    private ProcessedMessageRepository processedMessageRepository;

    @MockitoBean
    private EmailTemplateResolver templateResolver;

    @MockitoBean
    private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        given(templateResolver.resolveSubject(any())).willReturn("Subject");
        given(templateResolver.render(any(), any())).willReturn("<html>body</html>");
    }

    @AfterEach
    void cleanUp() {
        processedMessageRepository.deleteAll();
    }

    private EmailNotificationEvent buildEvent() {
        return new EmailNotificationEvent("msg-dispatch", null, "subscription-confirmation",
                "user@example.com", Map.of(), NOW);
    }

    @Test
    void sendsOnlyOnceAcrossRedeliveries() {
        EmailNotificationEvent event = buildEvent();

        emailDispatchService.dispatch(event);
        emailDispatchService.dispatch(event);

        then(emailSender).should().send("user@example.com", "Subject", "<html>body</html>");
        assertThat(processedMessageRepository.findAll()).hasSize(1);
    }

    @Test
    void rollsBackFailedDeliveryAndAllowsRetry() {
        EmailNotificationEvent event = buildEvent();
        EmailDeliveryException failure = new EmailDeliveryException("SMTP unavailable", new RuntimeException());
        willThrow(failure).willDoNothing().given(emailSender).send(any(), any(), any());

        assertThatThrownBy(() -> emailDispatchService.dispatch(event)).isSameAs(failure);
        assertThat(processedMessageRepository.findAll()).isEmpty();

        emailDispatchService.dispatch(event);

        assertThat(processedMessageRepository.findAll()).hasSize(1);
        then(emailSender).should(times(2)).send(any(), any(), any());
    }

    @Test
    void rollsBackWhenRenderingFails() {
        RuntimeException failure = new RuntimeException("Template rendering failed");
        given(templateResolver.render(any(), any())).willThrow(failure);

        assertThatThrownBy(() -> emailDispatchService.dispatch(buildEvent())).isSameAs(failure);

        assertThat(processedMessageRepository.findAll()).isEmpty();
        then(emailSender).shouldHaveNoInteractions();
    }

    @Test
    void sendsOnlyOnceAcrossConcurrentDeliveries() throws Exception {
        EmailNotificationEvent event = buildEvent();
        CountDownLatch start = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                start.await();
                emailDispatchService.dispatch(event);
                return null;
            });
            var second = pool.submit(() -> {
                start.await();
                emailDispatchService.dispatch(event);
                return null;
            });
            start.countDown();

            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        then(emailSender).should().send(any(), any(), any());
        assertThat(processedMessageRepository.findAll()).hasSize(1);
    }
}
