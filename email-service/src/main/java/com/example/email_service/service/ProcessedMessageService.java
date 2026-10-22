package com.example.email_service.service;

import com.example.email_service.repository.ProcessedMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@Transactional
@RequiredArgsConstructor
public class ProcessedMessageService {

    private final ProcessedMessageRepository processedMessageRepository;
    private final Clock clock;

    public boolean tryClaim(String messageId) {
        return processedMessageRepository.insertIfAbsent(messageId, clock.instant()) == 1;
    }
}

