package com.example.certificate_service.exception;

public class CertificateFileNotFoundException extends RuntimeException {

    public CertificateFileNotFoundException() {
        super("Arquivo do certificado não encontrado.");
    }
}
