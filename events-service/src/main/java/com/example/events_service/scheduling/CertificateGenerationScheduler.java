package com.example.events_service.scheduling;

import com.example.events_service.service.CertificateGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CertificateGenerationScheduler {

    private final CertificateGenerationService certificateGenerationService;

    @Scheduled(fixedDelayString = "${certificate.scheduler.delay-ms:60000}")
    public void requestCertificatesForFinishedEvents() {
        certificateGenerationService.requestCertificatesForFinishedEvents();
    }
}
