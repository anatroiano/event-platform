package com.example.email_service.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(
        name = "processed_messages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_processed_messages_message_id",
                columnNames = "message_id"
        )
)
@Getter
@NoArgsConstructor(access = PROTECTED)
public class ProcessedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "message_id", nullable = false, updatable = false, length = 64)
    private String messageId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    private ProcessedMessage(String messageId, Instant processedAt) {
        this.messageId = messageId;
        this.processedAt = processedAt;
    }

    public static ProcessedMessage from(String messageId, Instant processedAt) {
        return new ProcessedMessage(messageId, processedAt);
    }
}
