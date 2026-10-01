package com.example.email_service.repository;

import com.example.email_service.domain.ProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, Long> {

    void deleteByMessageId(String messageId);
}
