package com.example.certificate_service.messaging.listener;

import com.example.certificate_service.config.RabbitConfig;
import com.example.certificate_service.dto.CertificateRequest;
import com.example.certificate_service.service.CertificateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CertificateGenerationListener {

    private static final Logger log = LoggerFactory.getLogger(CertificateGenerationListener.class);

    private final CertificateService certificateService;

    @RabbitListener(queues = RabbitConfig.CERTIFICATE_QUEUE)
    public void handle(@Valid CertificateRequest request) {
        log.info("Certificate generation requested for subscriptionId: {}", request.subscriptionId());
        certificateService.generate(request);
    }

}
