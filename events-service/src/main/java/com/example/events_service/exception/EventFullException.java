package com.example.events_service.exception;

public class EventFullException extends RuntimeException {

    public EventFullException() {
        super("Evento com máximo de inscrições atingido!");
    }

}
