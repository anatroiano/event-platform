package com.example.events_service.exception;

public class SubscriptionNotFoundException extends RuntimeException {

    public SubscriptionNotFoundException() {
        super("Inscrição não encontrada");
    }

}
