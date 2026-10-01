package com.example.email_service.template;

public class UnknownTemplateException extends RuntimeException {

    public UnknownTemplateException(String templateId) {
        super("Unknown email template: " + templateId);
    }
}
