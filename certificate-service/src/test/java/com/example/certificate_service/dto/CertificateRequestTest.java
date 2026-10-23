package com.example.certificate_service.dto;

import com.example.certificate_service.domain.Certificate;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    static Stream<CertificateRequest> invalidRequests() {
        return Stream.of(
                new CertificateRequest(null, "sub", "User", "user@example.com", "Workshop"),
                new CertificateRequest("evt", " ", "User", "user@example.com", "Workshop"),
                new CertificateRequest("evt", "sub", null, "user@example.com", "Workshop"),
                new CertificateRequest("evt", "sub", "User", "invalid-email", "Workshop"),
                new CertificateRequest("evt", "sub", "User", "user@example.com", ""),
                new CertificateRequest("evt", "sub", "a".repeat(256), "user@example.com", "Workshop")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void validatesRequestAndDomainConstraints(CertificateRequest request) {
        Certificate certificate = Certificate.from(request, Instant.parse("2026-01-01T12:00:00Z"));

        assertThat(validator.validate(request)).isNotEmpty();
        assertThat(validator.validate(certificate)).isNotEmpty();
    }

    @Test
    void acceptsValidCertificateData() {
        CertificateRequest request = new CertificateRequest("evt", "sub", "User", "user@example.com", "Workshop");
        Certificate certificate = Certificate.from(request, Instant.parse("2026-01-01T12:00:00Z"));

        assertThat(validator.validate(request)).isEmpty();
        assertThat(validator.validate(certificate)).isEmpty();
    }
}
