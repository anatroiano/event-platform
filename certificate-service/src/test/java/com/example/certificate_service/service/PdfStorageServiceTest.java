package com.example.certificate_service.service;

import com.example.certificate_service.exception.CertificateFileNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PdfStorageService")
class PdfStorageServiceTest {

    @TempDir
    Path tempDir;

    private PdfStorageService service() {
        return new PdfStorageService(tempDir.toString());
    }

    @Nested
    @DisplayName("store()")
    class Store {

        @Test
        @DisplayName("writes the content to disk and returns the file key")
        void writesContentAndReturnsKey() {
            byte[] content = {1, 2, 3};

            String returned = service().store("certificates/test.pdf", content);

            assertThat(returned).isEqualTo("certificates/test.pdf");
            assertThat(tempDir.resolve("certificates/test.pdf")).exists();
        }

        @Test
        @DisplayName("creates intermediate directories when they do not yet exist")
        void createsIntermediateDirectories() {
            service().store("deep/nested/dir/cert.pdf", new byte[]{0});

            assertThat(tempDir.resolve("deep/nested/dir/cert.pdf")).exists();
        }

        @Test
        @DisplayName("throws IllegalArgumentException on path-traversal attempt")
        void throwsOnPathTraversal() {
            assertThatThrownBy(() -> service().store("../outside.pdf", new byte[]{0}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid file name");
        }
    }

    @Nested
    @DisplayName("load()")
    class Load {

        @Test
        @DisplayName("returns the exact bytes that were previously stored")
        void returnsPreviouslyStoredBytes() {
            byte[] content = {10, 20, 30};
            service().store("certificates/load-test.pdf", content);

            byte[] loaded = service().load("certificates/load-test.pdf");

            assertThat(loaded).isEqualTo(content);
        }

        @Test
        @DisplayName("throws CertificateFileNotFoundException when file does not exist")
        void throwsWhenFileNotFound() {
            assertThatThrownBy(() -> service().load("certificates/missing.pdf"))
                    .isInstanceOf(CertificateFileNotFoundException.class)
                    .hasMessage("Arquivo do certificado não encontrado.");
        }

        @Test
        @DisplayName("throws IllegalArgumentException on path-traversal attempt")
        void throwsOnPathTraversal() {
            assertThatThrownBy(() -> service().load("../secret.pdf"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid file name");
        }
    }

    @Test
    void deletesStoredFileAndAllowsRepeatedDeletion() {
        PdfStorageService storage = service();
        storage.store("certificates/delete.pdf", new byte[]{1});

        storage.delete("certificates/delete.pdf");
        storage.delete("certificates/delete.pdf");

        assertThat(tempDir.resolve("certificates/delete.pdf")).doesNotExist();
    }
}
