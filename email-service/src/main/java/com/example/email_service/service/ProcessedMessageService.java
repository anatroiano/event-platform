package com.example.email_service.service;

import com.example.email_service.domain.ProcessedMessage;
import com.example.email_service.repository.ProcessedMessageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@Service
public class ProcessedMessageService {

    private final ProcessedMessageRepository processedMessageRepository;
    private final TransactionTemplate transactionTemplate;

    public ProcessedMessageService(ProcessedMessageRepository processedMessageRepository,
                                   PlatformTransactionManager transactionManager) {
        this.processedMessageRepository = processedMessageRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public boolean tryClaim(String messageId) {
        try {
            transactionTemplate.executeWithoutResult(status -> processedMessageRepository
                    .saveAndFlush(ProcessedMessage.from(messageId, Instant.now())));

            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    public void release(String messageId) {
        transactionTemplate.executeWithoutResult(status ->
                processedMessageRepository.deleteByMessageId(messageId));
    }
}

