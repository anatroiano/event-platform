package com.example.certificate_service.service;

import com.example.certificate_service.domain.Certificate;
import com.example.certificate_service.dto.CertificateRequest;
import com.example.certificate_service.exception.CertificateNotFoundException;
import com.example.certificate_service.messaging.event.CertificateEmailNotificationEvent;
import com.example.certificate_service.repository.CertificateRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);

    private final CertificateRepository certificateRepository;

    private final PdfService pdfService;

    private final PdfStorageService pdfStorageService;

    private final OutboxService outboxService;

    private final Clock clock;

    @Value("${app.base-url}")
    private String baseUrl;

    public Certificate generate(CertificateRequest request) {

        log.info("Generating certificate for subscriptionId: {}", request.subscriptionId());

        Certificate candidate = Certificate.from(request, clock.instant());
        boolean created = certificateRepository.insertIfAbsent(candidate) == 1;
        Certificate certificate = certificateRepository
                .findBySubscriptionIdAndEventId(request.subscriptionId(), request.eventId())
                .orElseThrow(() -> new CertificateNotFoundException(candidate.getId()));

        if (!created) {
            log.info("Certificate already generated for subscriptionId: {}", request.subscriptionId());
            return certificate;
        }

        byte[] pdf = pdfService.generate("Certificado de Participação",
                certificate.getParticipantName(),
                certificate.getEventName(),
                certificate.getIssuedAt(),
                certificate.getId()
        );

        String fileKey = "certificates/" + certificate.getId() + ".pdf";
        deleteFileOnRollback(fileKey);
        pdfStorageService.store(fileKey, pdf);

        certificate.setFileKey(fileKey);

        String certificateUrl = baseUrl + "/certificates/" + certificate.getId() + "/download";

        CertificateEmailNotificationEvent notification = CertificateEmailNotificationEvent.of(
                certificate.getParticipantEmail(),
                Map.of(
                        "participantName", certificate.getParticipantName(),
                        "eventName", certificate.getEventName(),
                        "certificateUrl", certificateUrl
                ),
                certificate.getIssuedAt()
        );

        outboxService.save(notification);
        log.info("Certificate generated and notification queued for subscriptionId: {}", request.subscriptionId());

        return certificate;
    }

    @Transactional(readOnly = true)
    public Optional<byte[]> download(String certificateId) {
        Certificate certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new CertificateNotFoundException(certificateId));

        return Optional.ofNullable(certificate.getFileKey()).map(pdfStorageService::load);
    }

    private void deleteFileOnRollback(String fileKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        pdfStorageService.delete(fileKey);
                    } catch (RuntimeException ex) {
                        log.error("Failed to remove certificate PDF after rollback: fileKey={}", fileKey, ex);
                    }
                }
            }
        });
    }
}
