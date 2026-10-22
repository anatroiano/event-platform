package com.example.events_service.domain;

import com.example.events_service.dto.EventRequestDTO;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "event")
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Positive(message = "A capacidade do evento deve ser maior que zero.")
    private int maxParticipants;

    @NotNull(message = "A data do evento é obrigatória.")
    private Instant date;

    @NotBlank(message = "O título do evento é obrigatório.")
    @Size(max = 255, message = "O título do evento deve ter no máximo 255 caracteres.")
    private String title;

    @Size(max = 2000, message = "A descrição do evento deve ter no máximo 2000 caracteres.")
    private String description;

    private boolean certificateRequestsSent;

    protected Event() {
    }

    private Event(EventRequestDTO eventRequest) {
        this.maxParticipants = eventRequest.maxParticipants();
        this.date = eventRequest.date();
        this.title = eventRequest.title();
        this.description = eventRequest.description();
    }

    public static Event from(EventRequestDTO eventRequest) {
        return new Event(eventRequest);
    }

}
