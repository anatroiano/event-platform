package com.example.events_service.domain;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "subscription",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_subscription_event_email",
                columnNames = {"event_id", "participant_email"}
        )
)
@Getter
@EqualsAndHashCode(of = "id")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false)
    private String participantEmail;

    @Column(nullable = false, unique = true, updatable = false)
    private String checkinToken;

    private Instant checkedInAt;

    private Subscription() {
    }

    private Subscription(Event event, String participantEmail) {
        this.event = event;
        this.participantEmail = participantEmail;
        this.checkinToken = UUID.randomUUID().toString();
    }

    public static Subscription from(Event event, String participantEmail) {
        return new Subscription(event, participantEmail);
    }

    public boolean isCheckedIn() {
        return checkedInAt != null;
    }

    public void checkIn() {
        if (checkedInAt == null) {
            checkedInAt = Instant.now();
        }
    }
}
