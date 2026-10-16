package com.example.certificate_service.service;

import com.example.certificate_service.domain.Certificate;
import com.example.certificate_service.dto.CertificateRequest;
import com.example.certificate_service.messaging.publisher.CertificateNotificationPublisher;
import com.example.certificate_service.repository.CertificateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("CertificateService")
class CertificateServiceTest {

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private PdfService pdfService;

    @Mock
    private PdfStorageService pdfStorageService;

    @Mock
    private CertificateNotificationPublisher notificationPublisher;

    @InjectMocks
    private CertificateService certificateService;

    @BeforeEach
    void injectBaseUrl() {
        ReflectionTestUtils.setField(certificateService, "baseUrl", "https://cert.example.com");
    }

    private CertificateRequest buildRequest() {
        return new CertificateRequest(
                "evt-001",
                "sub-001",
                "John Doe",
                "john.doe@email.com",
                "Spring Boot Workshop"
        );
    }

    private Certificate buildSavedCertificate(CertificateRequest request) {
        Certificate certificate = Certificate.from(request);
        ReflectionTestUtils.setField(certificate, "id", "cert-uuid-001");
        return certificate;
    }

    @Nested
    @DisplayName("generate()")
    class Generate {

        @Test
        @DisplayName("throws IllegalStateException when certificate already exists for the same subscription and event")
        void throwsWhenCertificateAlreadyExists() {
            CertificateRequest request = buildRequest();
            given(certificateRepository.existsBySubscriptionIdAndEventId("sub-001", "evt-001")).willReturn(true);

            assertThatThrownBy(() -> certificateService.generate(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Certificate already exists");

            then(pdfService).should(never()).generate(any(), any(), any(), any(), any());
            then(pdfStorageService).should(never()).store(any(), any());
            then(notificationPublisher).should(never()).publish(any());
        }

        @Test
        @DisplayName("persists certificate, generates PDF, stores it and publishes notification on happy path")
        void generatesCertificateOnHappyPath() {
            CertificateRequest request = buildRequest();
            Certificate saved = buildSavedCertificate(request);

            given(certificateRepository.existsBySubscriptionIdAndEventId("sub-001", "evt-001")).willReturn(false);
            given(certificateRepository.save(any(Certificate.class))).willReturn(saved);
            given(pdfService.generate(any(), any(), any(), any(), any())).willReturn(new byte[]{1, 2, 3});

            Certificate result = certificateService.generate(request);

            assertThat(result.getId()).isEqualTo("cert-uuid-001");
            then(pdfService).should().generate(any(), any(), any(), any(), any());
            then(pdfStorageService).should().store("certificates/cert-uuid-001.pdf", new byte[]{1, 2, 3});
            then(notificationPublisher).should().publish(any());
        }

        @Test
        @DisplayName("saves the certificate twice: once before PDF generation and once after setting the fileKey")
        void savesCertificateTwice() {
            CertificateRequest request = buildRequest();
            Certificate saved = buildSavedCertificate(request);

            given(certificateRepository.existsBySubscriptionIdAndEventId("sub-001", "evt-001")).willReturn(false);
            given(certificateRepository.save(any(Certificate.class))).willReturn(saved);
            given(pdfService.generate(any(), any(), any(), any(), any())).willReturn(new byte[0]);

            certificateService.generate(request);

            then(certificateRepository).should(org.mockito.Mockito.times(2)).save(any(Certificate.class));
        }

        @Test
        @DisplayName("notification contains the correct recipient email and certificate download URL")
        void notificationContainsCorrectEmailAndUrl() {
            CertificateRequest request = buildRequest();
            Certificate saved = buildSavedCertificate(request);

            given(certificateRepository.existsBySubscriptionIdAndEventId("sub-001", "evt-001")).willReturn(false);
            given(certificateRepository.save(any(Certificate.class))).willReturn(saved);
            given(pdfService.generate(any(), any(), any(), any(), any())).willReturn(new byte[0]);

            certificateService.generate(request);

            ArgumentCaptor<com.example.certificate_service.messaging.event.CertificateEmailNotificationEvent> captor =
                    ArgumentCaptor.forClass(com.example.certificate_service.messaging.event.CertificateEmailNotificationEvent.class);
            then(notificationPublisher).should().publish(captor.capture());

            var notification = captor.getValue();
            assertThat(notification.recipientEmail()).isEqualTo("john.doe@email.com");
            assertThat(notification.templateData()).containsKey("certificateUrl");
            assertThat(notification.templateData().get("certificateUrl").toString())
                    .isEqualTo("https://cert.example.com/certificates/cert-uuid-001/download");
        }

        @Test
        @DisplayName("stores PDF under the key 'certificates/{id}.pdf'")
        void storesPdfUnderCorrectKey() {
            CertificateRequest request = buildRequest();
            Certificate saved = buildSavedCertificate(request);

            given(certificateRepository.existsBySubscriptionIdAndEventId("sub-001", "evt-001")).willReturn(false);
            given(certificateRepository.save(any(Certificate.class))).willReturn(saved);
            given(pdfService.generate(any(), any(), any(), any(), any())).willReturn(new byte[]{42});

            certificateService.generate(request);

            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            then(pdfStorageService).should().store(keyCaptor.capture(), any());
            assertThat(keyCaptor.getValue()).isEqualTo("certificates/cert-uuid-001.pdf");
        }
    }
}
