package com.example.certificate_service.domain;

import com.example.certificate_service.messaging.event.CertificateEmailNotificationEvent;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "outbox_message", indexes = @Index(name = "idx_outbox_pending", columnList = "processed_at, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxMessage {

    @Id
    private String id;

    @Column(nullable = false, length = 4000)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(nullable = false)
    private int attempts;

    public static OutboxMessage from(CertificateEmailNotificationEvent event, String payload) {
        OutboxMessage message = new OutboxMessage();
        message.id = event.messageId();
        message.payload = payload;
        message.createdAt = event.occurredAt();
        return message;
    }

    public void markAsSent(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public void registerFailure() {
        this.attempts++;
    }
}
