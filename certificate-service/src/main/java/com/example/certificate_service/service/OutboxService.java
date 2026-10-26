package com.example.certificate_service.service;

import com.example.certificate_service.domain.OutboxMessage;
import com.example.certificate_service.messaging.event.CertificateEmailNotificationEvent;
import com.example.certificate_service.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(propagation = Propagation.MANDATORY)
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;

    public void save(CertificateEmailNotificationEvent event) {
        outboxRepository.save(OutboxMessage.from(event, jsonMapper.writeValueAsString(event)));
    }
}
