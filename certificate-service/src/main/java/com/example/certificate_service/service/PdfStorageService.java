package com.example.certificate_service.service;

import com.example.certificate_service.exception.CertificateFileNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

@Service
public class PdfStorageService {
    private final Path storageDirectory;

    public PdfStorageService(@Value("${certificate.storage-path:./storage}") String storagePath) {
        this.storageDirectory = Path.of(storagePath).toAbsolutePath().normalize();
    }

    public String store(String fileName, byte[] content) {
        Path file = resolveFile(fileName);
        try {
            Files.createDirectories(file.getParent());
            Path temporaryFile = Files.createTempFile(file.getParent(), ".certificate-", ".tmp");
            try {
                Files.write(temporaryFile, content);
                Files.move(temporaryFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporaryFile);
            }

            return fileName;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store certificate PDF: " + fileName, e);
        }
    }

    public byte[] load(String fileKey) {
        Path file = resolveFile(fileKey);
        try {
            return Files.readAllBytes(file);
        } catch (NoSuchFileException e) {
            throw new CertificateFileNotFoundException();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load certificate PDF", e);
        }
    }

    public void delete(String fileKey) {
        Path file = resolveFile(fileKey);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete certificate PDF", e);
        }
    }

    private Path resolveFile(String fileKey) {
        return Optional.ofNullable(fileKey)
                .filter(key -> !key.isBlank())
                .map(storageDirectory::resolve)
                .map(Path::normalize)
                .filter(file -> file.startsWith(storageDirectory) && !file.equals(storageDirectory))
                .orElseThrow(() -> new IllegalArgumentException("Invalid file name"));
    }
}
