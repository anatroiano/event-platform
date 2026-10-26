package com.example.certificate_service.service;

import com.example.certificate_service.TestcontainersConfiguration;
import com.example.certificate_service.domain.Certificate;
import com.example.certificate_service.dto.CertificateRequest;
import com.example.certificate_service.dto.CertificateResponse;
import com.example.certificate_service.exception.CertificateNotFoundException;
import com.example.certificate_service.repository.CertificateRepository;
import com.example.certificate_service.repository.OutboxRepository;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({CertificateService.class, OutboxService.class, CertificateServiceIT.TestConfig.class,
        TestcontainersConfiguration.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CertificateServiceIT {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @TestConfiguration
    static class TestConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        Path certificateStorageDirectory() throws IOException {
            return Files.createTempDirectory("certificate-it-");
        }

        @Bean
        PdfStorageService pdfStorageService(@Qualifier("certificateStorageDirectory") Path directory) {
            return new PdfStorageService(directory.toString());
        }
    }

    @MockitoBean
    private PdfService pdfService;

    @Autowired
    private Flyway flyway;

    @Autowired
    private CertificateService certificateService;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private PdfStorageService pdfStorageService;
    @Autowired
    @Qualifier("certificateStorageDirectory")
    private Path storageDirectory;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(certificateService, "frontendUrl", "https://app.example.com");
        given(pdfService.generate(any(), any(), any(), any(), any())).willReturn(new byte[]{1, 2, 3});
    }

    @AfterEach
    void cleanUp() throws IOException {
        outboxRepository.deleteAll();
        certificateRepository.deleteAll();
        try (var files = Files.walk(storageDirectory)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.equals(storageDirectory)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private CertificateRequest request(String eventId, String subscriptionId) {
        return new CertificateRequest(eventId, subscriptionId, "John Doe", "john.doe@email.com", "Workshop");
    }

    @Test
    void appliesVersionedSchemaMigrations() {
        assertThat(flyway.info().applied()).extracting(MigrationInfo::getScript)
                .contains("V1__create_initial_schema.sql");
        assertThat(flyway.info().pending()).isEmpty();
        flyway.validate();
    }

    @Test
    void returnsDetailsFromPersistedCertificate() {
        Certificate certificate = certificateService.generate(request("evt-001", "sub-001"));

        CertificateResponse details = certificateService.getDetails(certificate.getId());

        assertThat(details.id()).isEqualTo(certificate.getId());
        assertThat(details.eventName()).isEqualTo("Workshop");
        assertThat(details.issuedAt()).isEqualTo(NOW);
        assertThat(details.participantName()).isEqualTo("John Doe");
    }

    @Test
    void rejectsUnknownCertificateDetails() {
        assertThatThrownBy(() -> certificateService.getDetails("missing"))
                .isInstanceOf(CertificateNotFoundException.class);
    }

    @Test
    void commitsCertificatePdfAndNotificationTogether() {
        Certificate result = certificateService.generate(request("evt-001", "sub-001"));

        Certificate persisted = certificateRepository.findById(result.getId()).orElseThrow();
        assertThat(persisted.getParticipantName()).isEqualTo("John Doe");
        assertThat(persisted.getIssuedAt()).isEqualTo(NOW);
        assertThat(pdfStorageService.load(persisted.getFileKey())).isEqualTo(new byte[]{1, 2, 3});
        assertThat(outboxRepository.findAll()).singleElement().satisfies(message -> {
            assertThat(message.getProcessedAt()).isNull();
            assertThat(message.getPayload()).contains("john.doe@email.com", result.getId());
            assertThat(JsonMapper.builder().build().readTree(message.getPayload())
                    .at("/templateData/certificateUrl").asText())
                    .isEqualTo("https://app.example.com/certificates/" + result.getId());
        });
    }

    @Test
    void redeliveryReturnsSameCertificateWithoutDuplicatingNotification() {
        CertificateRequest request = request("evt-001", "sub-001");

        Certificate first = certificateService.generate(request);
        Certificate second = certificateService.generate(request);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(certificateRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
        then(pdfService).should().generate(any(), any(), any(), any(), any());
    }

    @Test
    void allowsDifferentSubscriptionAndEventCombinations() {
        certificateService.generate(request("evt-001", "sub-001"));
        certificateService.generate(request("evt-001", "sub-002"));
        certificateService.generate(request("evt-002", "sub-001"));

        assertThat(certificateRepository.count()).isEqualTo(3);
        assertThat(outboxRepository.count()).isEqualTo(3);
    }

    @Test
    void rollsBackCertificateWhenPdfGenerationFails() {
        RuntimeException failure = new RuntimeException("PDF failed");
        given(pdfService.generate(any(), any(), any(), any(), any())).willThrow(failure);

        assertThatThrownBy(() -> certificateService.generate(request("evt-001", "sub-001"))).isSameAs(failure);

        assertThat(certificateRepository.findAll()).isEmpty();
        assertThat(outboxRepository.findAll()).isEmpty();
    }

    @Test
    void rollsBackCertificateAndRemovesPdfWhenOutboxWriteFails() throws IOException {
        ReflectionTestUtils.setField(certificateService, "frontendUrl", "https://app.example.com/" + "a".repeat(4500));

        assertThatThrownBy(() -> certificateService.generate(request("evt-001", "sub-001")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(certificateRepository.findAll()).isEmpty();
        assertThat(outboxRepository.findAll()).isEmpty();
        try (var files = Files.walk(storageDirectory)) {
            assertThat(files.filter(Files::isRegularFile).toList()).isEmpty();
        }
    }

    @Test
    void concurrentRequestsProduceOneCertificateAndNotification() throws Exception {
        CertificateRequest request = request("evt-001", "sub-001");
        CountDownLatch start = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                start.await();
                return certificateService.generate(request);
            });
            var second = pool.submit(() -> {
                start.await();
                return certificateService.generate(request);
            });
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS).getId())
                    .isEqualTo(second.get(10, TimeUnit.SECONDS).getId());
        }

        assertThat(certificateRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
        then(pdfService).should().generate(any(), any(), any(), any(), any());
    }
}
