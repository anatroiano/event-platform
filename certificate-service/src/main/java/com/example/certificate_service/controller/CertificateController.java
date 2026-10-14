package com.example.certificate_service.controller;

import com.example.certificate_service.domain.Certificate;
import com.example.certificate_service.repository.CertificateRepository;
import com.example.certificate_service.service.PdfStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Certificates", description = "Operations related to certificate download")
@RestController
@RequestMapping("/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private static final Logger log = LoggerFactory.getLogger(CertificateController.class);

    private final CertificateRepository certificateRepository;
    private final PdfStorageService pdfStorageService;

    @Operation(summary = "Download certificate PDF", description = "Downloads the PDF certificate for the given certificate ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Certificate PDF returned"),
            @ApiResponse(responseCode = "404", description = "Certificate not found")
    })
    @GetMapping("/{certificateId}/download")
    public ResponseEntity<byte[]> download(@PathVariable String certificateId) {

        log.info("Certificate download requested: {}", certificateId);

        Certificate certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> {
                    log.warn("Certificate not found: {}", certificateId);
                    return new CertificateNotFoundException(certificateId);
                });

        if (certificate.getFileKey() == null) {
            log.warn("Certificate file not yet available: {}", certificateId);
            return ResponseEntity.accepted().build();
        }

        byte[] pdf = pdfStorageService.load(certificate.getFileKey());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"certificate-" + certificateId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
