package com.example.certificate_service.service;

import com.example.certificate_service.domain.Certificate;
import com.example.certificate_service.dto.CertificateRequest;
import com.example.certificate_service.messaging.event.CertificateEmailNotificationEvent;
import com.example.certificate_service.messaging.publisher.CertificateNotificationPublisher;
import com.example.certificate_service.repository.CertificateRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Transactional
@RequiredArgsConstructor
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);

    private final CertificateRepository certificateRepository;

    private final PdfService pdfService;

    private final PdfStorageService pdfStorageService;

    private final CertificateNotificationPublisher notificationPublisher;

    @Value("${app.base-url}")
    private String baseUrl;

    public Certificate generate(CertificateRequest request) {

        log.info("Generating certificate for subscriptionId: {}", request.subscriptionId());

        if (certificateRepository.existsBySubscriptionIdAndEventId(request.subscriptionId(), request.eventId())) {
            log.info("Certificate already exists for subscriptionId: {}", request.subscriptionId());
            throw new IllegalStateException("Certificate already exists for this subscription and event");
        }

        Certificate certificate = certificateRepository.save(Certificate.from(request));

        byte[] pdf = pdfService.generate("Certificado de Participação",
                certificate.getParticipantName(),
                certificate.getEventName(),
                certificate.getIssuedAt(),
                certificate.getId()
        );

        String fileKey = "certificates/" + certificate.getId() + ".pdf";
        pdfStorageService.store(fileKey, pdf);

        certificate.setFileKey(fileKey);
        certificateRepository.save(certificate);

        String certificateUrl = baseUrl + "/certificates/" + certificate.getId() + "/download";

        CertificateEmailNotificationEvent notification = CertificateEmailNotificationEvent.of(
                certificate.getParticipantEmail(),
                Map.of(
                        "participantName", certificate.getParticipantName(),
                        "eventName", certificate.getEventName(),
                        "certificateUrl", certificateUrl
                )
        );

        notificationPublisher.publish(notification);
        log.info("Certificate generated and notification published for subscriptionId: {}", request.subscriptionId());

        return certificate;
    }
}
