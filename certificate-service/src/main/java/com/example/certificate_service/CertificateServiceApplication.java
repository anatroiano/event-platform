package com.example.certificate_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CertificateServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CertificateServiceApplication.class, args);
    }

}
