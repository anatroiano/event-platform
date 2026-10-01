package com.example.events_service.service;

import com.example.events_service.domain.OutboxMessage;
import com.example.events_service.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;

    public void save(String routingKey, Object payload) {

        log.info("Starting outbox save - routingKey: {}", routingKey);

        String json = jsonMapper.writeValueAsString(payload);
        outboxRepository.save(OutboxMessage.of(routingKey, json));
    }
}