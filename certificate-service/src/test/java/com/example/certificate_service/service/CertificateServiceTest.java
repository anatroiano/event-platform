package com.example.certificate_service.service;

import com.example.certificate_service.domain.Certificate;
import com.example.certificate_service.dto.CertificateRequest;
import com.example.certificate_service.dto.CertificateResponse;
import com.example.certificate_service.exception.CertificateNotFoundException;
import com.example.certificate_service.messaging.event.CertificateEmailNotificationEvent;
import com.example.certificate_service.repository.CertificateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private CertificateRepository certificateRepository;
    @Mock
    private PdfService pdfService;
    @Mock
    private PdfStorageService pdfStorageService;
    @Mock
    private OutboxService outboxService;

    private CertificateService certificateService;

    @BeforeEach
    void setUp() {
        certificateService = new CertificateService(certificateRepository, pdfService, pdfStorageService,
                outboxService, Clock.fixed(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(certificateService, "frontendUrl", "https://app.example.com");
    }

    private CertificateRequest buildRequest() {
        return new CertificateRequest("evt-001", "sub-001", "John Doe", "john.doe@email.com", "Workshop");
    }

    private Certificate prepareNewCertificate() {
        Certificate saved = Certificate.from(buildRequest(), NOW);
        given(certificateRepository.insertIfAbsent(any())).willReturn(1);
        given(certificateRepository.findBySubscriptionIdAndEventId("sub-001", "evt-001"))
                .willReturn(Optional.of(saved));
        return saved;
    }

    @Test
    void returnsExistingCertificateWithoutRegeneratingOrQueuingNotification() {
        Certificate existing = Certificate.from(buildRequest(), NOW);
        given(certificateRepository.insertIfAbsent(any())).willReturn(0);
        given(certificateRepository.findBySubscriptionIdAndEventId("sub-001", "evt-001"))
                .willReturn(Optional.of(existing));

        assertThat(certificateService.generate(buildRequest())).isSameAs(existing);

        then(pdfService).shouldHaveNoInteractions();
        then(pdfStorageService).shouldHaveNoInteractions();
        then(outboxService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://app.example.com", "https://app.example.com/"})
    void generatesPdfAndQueuesNotificationWithFrontendUrl(String frontendUrl) {
        ReflectionTestUtils.setField(certificateService, "frontendUrl", frontendUrl);
        Certificate saved = prepareNewCertificate();
        byte[] pdf = {1, 2, 3};
        given(pdfService.generate(any(), any(), any(), any(), any())).willReturn(pdf);

        Certificate result = certificateService.generate(buildRequest());

        assertThat(result).isSameAs(saved);
        assertThat(result.getFileKey()).isEqualTo("certificates/" + saved.getId() + ".pdf");
        then(pdfService).should().generate("Certificado de Participação", "John Doe", "Workshop", NOW, saved.getId());
        then(pdfStorageService).should().store(result.getFileKey(), pdf);
        ArgumentCaptor<CertificateEmailNotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(CertificateEmailNotificationEvent.class);
        then(outboxService).should().save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().recipientEmail()).isEqualTo("john.doe@email.com");
        assertThat(eventCaptor.getValue().occurredAt()).isEqualTo(NOW);
        assertThat(eventCaptor.getValue().templateData()).containsEntry("certificateUrl",
                "https://app.example.com/certificates/" + saved.getId());
    }

    @Test
    void propagatesPdfGenerationFailureWithoutStoringOrNotifying() {
        prepareNewCertificate();
        RuntimeException failure = new RuntimeException("PDF failed");
        given(pdfService.generate(any(), any(), any(), any(), any())).willThrow(failure);

        assertThatThrownBy(() -> certificateService.generate(buildRequest())).isSameAs(failure);

        then(pdfStorageService).shouldHaveNoInteractions();
        then(outboxService).shouldHaveNoInteractions();
    }

    @Test
    void doesNotQueueNotificationWhenStorageFails() {
        prepareNewCertificate();
        given(pdfService.generate(any(), any(), any(), any(), any())).willReturn(new byte[]{1});
        RuntimeException failure = new RuntimeException("Storage failed");
        willThrow(failure).given(pdfStorageService).store(any(), any());

        assertThatThrownBy(() -> certificateService.generate(buildRequest())).isSameAs(failure);

        then(outboxService).shouldHaveNoInteractions();
    }

    @Test
    void returnsCertificateDetailsWithoutLoadingPdf() {
        Certificate certificate = Certificate.from(buildRequest(), NOW);
        given(certificateRepository.findById(certificate.getId())).willReturn(Optional.of(certificate));

        CertificateResponse details = certificateService.getDetails(certificate.getId());

        assertThat(details.id()).isEqualTo(certificate.getId());
        assertThat(details.eventName()).isEqualTo("Workshop");
        assertThat(details.issuedAt()).isEqualTo(NOW);
        assertThat(details.participantName()).isEqualTo("John Doe");
        then(pdfService).shouldHaveNoInteractions();
        then(pdfStorageService).shouldHaveNoInteractions();
        then(outboxService).shouldHaveNoInteractions();
    }

    @Test
    void rejectsDetailsRequestWhenCertificateDoesNotExist() {
        given(certificateRepository.findById("missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> certificateService.getDetails("missing"))
                .isInstanceOf(CertificateNotFoundException.class);
    }

    @Test
    void rejectsDownloadWhenCertificateDoesNotExist() {
        given(certificateRepository.findById("missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> certificateService.download("missing"))
                .isInstanceOf(CertificateNotFoundException.class);

        then(pdfStorageService).shouldHaveNoInteractions();
    }

    @Test
    void returnsEmptyWhenCertificateHasNoFileYet() {
        Certificate certificate = Certificate.from(buildRequest(), NOW);
        given(certificateRepository.findById(certificate.getId())).willReturn(Optional.of(certificate));

        assertThat(certificateService.download(certificate.getId())).isEmpty();
        then(pdfStorageService).shouldHaveNoInteractions();
    }

    @Test
    void loadsPdfForExistingCertificate() {
        Certificate certificate = Certificate.from(buildRequest(), NOW);
        certificate.setFileKey("certificates/test.pdf");
        byte[] pdf = {1, 2, 3};
        given(certificateRepository.findById(certificate.getId())).willReturn(Optional.of(certificate));
        given(pdfStorageService.load(certificate.getFileKey())).willReturn(pdf);

        assertThat(certificateService.download(certificate.getId())).contains(pdf);
    }
}
