package com.example.certificate_service.controller;

import com.example.certificate_service.domain.Certificate;
import com.example.certificate_service.dto.CertificateRequest;
import com.example.certificate_service.repository.CertificateRepository;
import com.example.certificate_service.service.PdfStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CertificateController.class)
@DisplayName("CertificateController")
class CertificateControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CertificateRepository certificateRepository;

    @MockitoBean
    private PdfStorageService pdfStorageService;

    private Certificate buildCertificateWithFileKey(String id, String fileKey) {
        Certificate certificate = Certificate.from(new CertificateRequest(
                "evt-001", "sub-001", "John Doe", "john.doe@email.com", "Spring Boot Workshop"
        ));
        ReflectionTestUtils.setField(certificate, "id", id);
        certificate.setFileKey(fileKey);
        return certificate;
    }

    @Nested
    @DisplayName("GET /certificates/{certificateId}/download")
    class Download {

        @Test
        @DisplayName("returns 200 with PDF content and correct headers when certificate exists")
        void returns200WithPdfWhenCertificateExists() throws Exception {
            Certificate certificate = buildCertificateWithFileKey("cert-001", "certificates/cert-001.pdf");
            given(certificateRepository.findById("cert-001")).willReturn(Optional.of(certificate));
            given(pdfStorageService.load("certificates/cert-001.pdf")).willReturn(new byte[]{1, 2, 3});

            mockMvc.perform(get("/certificates/cert-001/download"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                    .andExpect(header().string("Content-Disposition", "attachment; filename=\"certificate-cert-001.pdf\""));
        }

        @Test
        @DisplayName("returns 404 when certificate id does not exist")
        void returns404WhenCertificateNotFound() throws Exception {
            given(certificateRepository.findById("unknown-id")).willReturn(Optional.empty());

            mockMvc.perform(get("/certificates/unknown-id/download"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("returns 202 when certificate exists but file is not yet available")
        void returns202WhenFileKeyIsNull() throws Exception {
            Certificate certificate = Certificate.from(new CertificateRequest(
                    "evt-001", "sub-001", "John Doe", "john.doe@email.com", "Spring Boot Workshop"
            ));
            ReflectionTestUtils.setField(certificate, "id", "cert-002");
            given(certificateRepository.findById("cert-002")).willReturn(Optional.of(certificate));

            mockMvc.perform(get("/certificates/cert-002/download"))
                    .andExpect(status().isAccepted());
        }
    }
}
