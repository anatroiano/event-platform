package com.example.certificate_service.controller;

public class CertificateNotFoundException extends RuntimeException {

    public CertificateNotFoundException(String certificateId) {
        super("Certificate not found: " + certificateId);
    }
}
