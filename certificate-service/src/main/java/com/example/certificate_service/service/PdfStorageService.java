package com.example.certificate_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class PdfStorageService {
    private final Path storageDirectory;

    public PdfStorageService(@Value("${certificate.storage-path:./storage}") String storagePath) {
        this.storageDirectory = Path.of(storagePath).toAbsolutePath().normalize();
    }

    public String store(String fileName, byte[] content) {
        try {
            Path file = storageDirectory.resolve(fileName).normalize();

            if (!file.startsWith(storageDirectory)) {
                throw new IllegalArgumentException("Invalid file name");
            }

            Files.createDirectories(file.getParent());

            Files.write(file, content);

            return fileName;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store certificate PDF: " + fileName, e);
        }
    }

    public byte[] load(String fileKey) {
        try {
            Path file = storageDirectory.resolve(fileKey).normalize();
            if (!file.startsWith(storageDirectory) || !Files.exists(file)) {
                throw new IllegalArgumentException("Certificate file not found");
            }
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load certificate PDF", e);
        }
    }
}