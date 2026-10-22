package com.example.events_service.domain;

import com.example.events_service.exception.InvalidDataException;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.Optional;
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
    @NotNull(message = "O evento da inscrição é obrigatório.")
    private Event event;

    @Column(nullable = false)
    @NotBlank(message = "O e-mail do participante é obrigatório.")
    @Email(message = "O e-mail do participante deve ser válido.")
    @Size(max = 255, message = "O e-mail do participante deve ter no máximo 255 caracteres.")
    private String participantEmail;

    @Size(max = 255, message = "O nome do participante deve ter no máximo 255 caracteres.")
    private String participantName;

    @Column(nullable = false, unique = true, updatable = false)
    private String checkinToken;

    private Instant checkedInAt;

    private Subscription() {
    }

    private Subscription(Event event, String participantEmail, String participantName) {
        this.event = event;
        this.participantEmail = participantEmail;
        this.participantName = participantName;
        this.checkinToken = UUID.randomUUID().toString();
    }

    public static Subscription from(Event event, String participantEmail, String participantName) {
        return new Subscription(event, participantEmail, participantName);
    }

    public boolean isCheckedIn() {
        return Optional.ofNullable(checkedInAt).isPresent();
    }

    public void checkIn(Instant checkedInAt) {
        Instant checkinTime = Optional.ofNullable(checkedInAt)
                .orElseThrow(() -> new InvalidDataException("O horário do check-in é obrigatório."));
        this.checkedInAt = Optional.ofNullable(this.checkedInAt).orElse(checkinTime);
    }
}
