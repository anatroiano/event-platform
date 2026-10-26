package com.example.email_service.repository;

import com.example.email_service.domain.ProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO processed_messages (message_id, processed_at)
            VALUES (:messageId, :processedAt)
            ON CONFLICT (message_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("messageId") String messageId, @Param("processedAt") Instant processedAt);
}
