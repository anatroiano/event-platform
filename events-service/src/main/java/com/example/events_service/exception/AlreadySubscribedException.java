package com.example.events_service.exception;

public class AlreadySubscribedException extends RuntimeException {
    public AlreadySubscribedException() {
        super("Participante já está inscrito neste evento!");
    }
}
