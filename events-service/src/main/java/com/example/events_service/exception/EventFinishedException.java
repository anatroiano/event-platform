package com.example.events_service.exception;

public class EventFinishedException extends RuntimeException {

    public EventFinishedException() {
        super("Evento já finalizado!");
    }
}
