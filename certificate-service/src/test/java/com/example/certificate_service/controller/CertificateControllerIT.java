package com.example.certificate_service.controller;

import com.example.certificate_service.exception.CertificateFileNotFoundException;
import com.example.certificate_service.exception.CertificateNotFoundException;
import com.example.certificate_service.service.CertificateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CertificateController.class)
class CertificateControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CertificateService certificateService;

    @Test
    void returnsPdfWithDownloadHeaders() throws Exception {
        byte[] pdf = {1, 2, 3};
        given(certificateService.download("cert-001")).willReturn(Optional.of(pdf));

        mockMvc.perform(get("/certificates/cert-001/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(pdf))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"certificate-cert-001.pdf\""));
    }

    @Test
    void returns404WhenCertificateDoesNotExist() throws Exception {
        given(certificateService.download("missing")).willThrow(new CertificateNotFoundException("missing"));

        mockMvc.perform(get("/certificates/missing/download"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void returns404WhenPdfIsMissingFromStorage() throws Exception {
        given(certificateService.download("cert-001")).willThrow(new CertificateFileNotFoundException());

        mockMvc.perform(get("/certificates/cert-001/download"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Arquivo do certificado não encontrado."));
    }

    @Test
    void returns202WhenPdfIsNotYetAvailable() throws Exception {
        given(certificateService.download("cert-002")).willReturn(Optional.empty());

        mockMvc.perform(get("/certificates/cert-002/download"))
                .andExpect(status().isAccepted());
    }
}
