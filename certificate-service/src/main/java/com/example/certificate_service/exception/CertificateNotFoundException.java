package com.example.certificate_service.exception;

public class CertificateNotFoundException extends RuntimeException {

    public CertificateNotFoundException(String certificateId) {
        super("Certificado não encontrado: " + certificateId);
    }
}
