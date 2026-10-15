package com.example.certificate_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CertificateServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
