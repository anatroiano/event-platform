package com.example.certificate_service.service;

import com.example.certificate_service.TestcontainersConfiguration;
import com.example.certificate_service.domain.Certificate;
import com.example.certificate_service.dto.CertificateRequest;
import com.example.certificate_service.messaging.publisher.CertificateNotificationPublisher;
import com.example.certificate_service.repository.CertificateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@DataJpaTest
@Import({
        CertificateService.class,
        CertificateServiceIT.TestConfig.class,
        TestcontainersConfiguration.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("CertificateService — integration")
class CertificateServiceIT {

    @TestConfiguration
    static class TestConfig {

        @Bean
        PdfStorageService pdfStorageService() throws Exception {
            java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("cert-it-storage");
            return new PdfStorageService(tempDir.toString());
        }
    }

    @MockitoBean
    private PdfService pdfService;

    @MockitoBean
    private CertificateNotificationPublisher notificationPublisher;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private CertificateRepository certificateRepository;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(certificateService, "baseUrl", "https://cert.example.com");
        certificateRepository.deleteAll();
        given(pdfService.generate(any(), any(), any(), any(), any())).willReturn(new byte[]{1, 2, 3});
    }

    private CertificateRequest buildRequest(String eventId, String subscriptionId) {
        return new CertificateRequest(
                eventId,
                subscriptionId,
                "John Doe",
                "john.doe@email.com",
                "Spring Boot Workshop"
        );
    }

    @Nested
    @DisplayName("generate()")
    class Generate {

        @Test
        @DisplayName("persists certificate with correct fields on first call")
        void persistsCertificateWithCorrectFields() {
            CertificateRequest request = buildRequest("evt-001", "sub-001");

            certificateService.generate(request);

            List<Certificate> all = certificateRepository.findAll();
            assertThat(all).hasSize(1);
            Certificate saved = all.get(0);
            assertThat(saved.getEventId()).isEqualTo("evt-001");
            assertThat(saved.getSubscriptionId()).isEqualTo("sub-001");
            assertThat(saved.getParticipantName()).isEqualTo("John Doe");
            assertThat(saved.getParticipantEmail()).isEqualTo("john.doe@email.com");
            assertThat(saved.getIssuedAt()).isNotNull();
        }

        @Test
        @DisplayName("sets fileKey on the persisted certificate after PDF is stored")
        void setsFileKeyAfterPdfStorage() {
            CertificateRequest request = buildRequest("evt-001", "sub-001");

            certificateService.generate(request);

            Certificate saved = certificateRepository.findAll().get(0);
            assertThat(saved.getFileKey()).startsWith("certificates/");
            assertThat(saved.getFileKey()).endsWith(".pdf");
        }

        @Test
        @DisplayName("throws IllegalStateException on duplicate subscriptionId + eventId and does not persist a second certificate")
        void throwsAndDoesNotPersistOnDuplicate() {
            CertificateRequest request = buildRequest("evt-001", "sub-001");
            certificateService.generate(request);

            assertThatThrownBy(() -> certificateService.generate(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Certificate already exists");

            assertThat(certificateRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("allows generating certificates for the same event with different subscriptions")
        void allowsDifferentSubscriptionsInSameEvent() {
            certificateService.generate(buildRequest("evt-001", "sub-001"));
            certificateService.generate(buildRequest("evt-001", "sub-002"));

            assertThat(certificateRepository.findAll()).hasSize(2);
        }

        @Test
        @DisplayName("allows generating certificates for the same subscription in different events")
        void allowsSameSubscriptionInDifferentEvents() {
            certificateService.generate(buildRequest("evt-001", "sub-001"));
            certificateService.generate(buildRequest("evt-002", "sub-001"));

            assertThat(certificateRepository.findAll()).hasSize(2);
        }
    }
}
