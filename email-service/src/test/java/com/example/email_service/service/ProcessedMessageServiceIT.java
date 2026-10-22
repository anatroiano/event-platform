package com.example.email_service.service;

import com.example.email_service.domain.ProcessedMessage;
import com.example.email_service.repository.ProcessedMessageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@Import({ProcessedMessageService.class, ProcessedMessageServiceIT.TestConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProcessedMessageServiceIT {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @TestConfiguration
    static class TestConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired
    private ProcessedMessageService processedMessageService;

    @Autowired
    private ProcessedMessageRepository processedMessageRepository;

    @AfterEach
    void cleanUp() {
        processedMessageRepository.deleteAll();
    }

    @Nested
    @DisplayName("tryClaim()")
    class TryClaim {

        @Test
        @DisplayName("returns true and persists the record on first call")
        void returnsTrueOnFirstClaim() {
            boolean claimed = processedMessageService.tryClaim("msg-001");

            assertThat(claimed).isTrue();

            List<ProcessedMessage> all = processedMessageRepository.findAll();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).getMessageId()).isEqualTo("msg-001");
            assertThat(all.get(0).getProcessedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("returns false only when the messageId already exists")
        void returnsFalseOnDuplicateClaim() {
            processedMessageService.tryClaim("msg-002");

            boolean secondAttempt = processedMessageService.tryClaim("msg-002");

            assertThat(secondAttempt).isFalse();
            assertThat(processedMessageRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("allows claiming different messageIds independently")
        void allowsClaimingDifferentIds() {
            boolean first = processedMessageService.tryClaim("msg-A");
            boolean second = processedMessageService.tryClaim("msg-B");

            assertThat(first).isTrue();
            assertThat(second).isTrue();
            assertThat(processedMessageRepository.findAll()).hasSize(2);
        }

        @Test
        @DisplayName("only one of N concurrent claims for the same messageId succeeds")
        void onlyOneConcurrentClaimWins() throws Exception {
            int threads = 8;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);

            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return processedMessageService.tryClaim("msg-concurrent");
                }));
            }
            start.countDown();

            long winners = 0;
            for (Future<Boolean> f : results) {
                if (f.get()) winners++;
            }
            pool.shutdown();

            assertThat(winners).isEqualTo(1);
            assertThat(processedMessageRepository.findAll()).hasSize(1);
        }
    }

    @Test
    @DisplayName("propagates other integrity failures instead of treating them as duplicates")
    void propagatesInvalidMessageIdFailure() {
        assertThatThrownBy(() -> processedMessageService.tryClaim("a".repeat(65)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(processedMessageRepository.findAll()).isEmpty();
    }
}
