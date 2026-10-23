package com.example.certificate_service.controller;

import com.example.certificate_service.exception.CertificateFileNotFoundException;
import com.example.certificate_service.exception.CertificateNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CertificateNotFoundException.class)
    public ProblemDetail handleNotFound(CertificateNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(CertificateFileNotFoundException.class)
    public ProblemDetail handleFileNotFound(CertificateFileNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }
}
