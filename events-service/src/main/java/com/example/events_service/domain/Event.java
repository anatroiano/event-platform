package com.example.events_service.domain;

import com.example.events_service.dto.EventRequestDTO;
import jakarta.persistence.*;
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

    private int maxParticipants;

    private Instant date;

    private String title;

    private String description;

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
