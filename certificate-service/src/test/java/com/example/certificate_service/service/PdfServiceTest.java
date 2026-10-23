package com.example.certificate_service.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PdfServiceTest {

    @Test
    void producesReadablePdfWithParticipantEventAndIssueDate() throws Exception {
        byte[] pdf = new PdfService().generate("Certificado de Participação", "João da Silva", "Workshop",
                Instant.parse("2026-01-01T12:00:00Z"), "cert-001");

        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(document))
                    .contains("João da Silva", "Workshop", "01 de janeiro de 2026", "cert-001");
        }
    }
}
