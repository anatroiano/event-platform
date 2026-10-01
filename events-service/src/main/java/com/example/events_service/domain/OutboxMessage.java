package com.example.events_service.domain;

import com.example.events_service.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "outbox_message",
        indexes = @Index(name = "idx_outbox_status_created", columnList = "status, createdAt")
)
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxMessage {

    @Id
    private String id;

    @Column(nullable = false)
    private String routingKey;

    @Column(nullable = false, length = 4000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    private int attempts;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant processedAt;

    public static OutboxMessage of(String routingKey, String payload) {
        OutboxMessage m = new OutboxMessage();
        m.id = UUID.randomUUID().toString();
        m.routingKey = routingKey;
        m.payload = payload;
        m.status = OutboxStatus.PENDING;
        m.createdAt = Instant.now();
        return m;
    }

    public void markAsSent() {
        this.status = OutboxStatus.SENT;
        this.processedAt = Instant.now();
    }

    public void registerFailure(int maxAttempts) {
        this.attempts++;
        if (this.attempts >= maxAttempts) {
            this.status = OutboxStatus.FAILED;
        }
    }
}