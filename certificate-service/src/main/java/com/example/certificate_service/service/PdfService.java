package com.example.certificate_service.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

@Service
public class PdfService {

    private static final Color NAVY = new Color(23, 43, 77);
    private static final Color GOLD = new Color(196, 155, 72);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))
            .withZone(ZoneId.of("America/Sao_Paulo"));

    public byte[] generate(String title, String participantName, String eventName, Instant issuedAt, String identifier) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream();
             InputStream regularFont = loadFontResource("/fonts/Roboto-Regular.ttf");
             InputStream boldFont = loadFontResource("/fonts/Roboto-Bold.ttf")) {
            document.addPage(new PDPage(new PDRectangle(842, 595)));

            PDType0Font regular = PDType0Font.load(document, regularFont);
            PDType0Font bold = PDType0Font.load(document, boldFont);

            try (PDPageContentStream content = new PDPageContentStream(document, document.getPage(0))) {
                content.setStrokingColor(GOLD);
                content.setLineWidth(2);
                content.addRect(25, 25, 792, 545);
                content.stroke();

                drawCentered(content, bold, 22, NAVY, title, 421);
                drawCentered(content, regular, 12, NAVY, "Certificamos que", 365);
                drawCentered(content, bold, 25, NAVY, participantName, 315);

                content.setStrokingColor(GOLD);
                content.setLineWidth(1);
                content.moveTo(150, 295);
                content.lineTo(692, 295);
                content.stroke();

                drawCentered(content, regular, 13, NAVY, "Participou do evento", 250);
                drawCentered(content, bold, 20, NAVY, eventName, 215);
                drawCentered(content, regular, 12, NAVY, "Emitido em " + DATE_FORMATTER.format(issuedAt), 165);
                drawCentered(content, regular, 9, NAVY, "Código de validação: " + identifier, 85);
            }

            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate PDF", e);
        }
    }

    private InputStream loadFontResource(String resource) {
        return Optional.ofNullable(getClass().getResourceAsStream(resource))
                .orElseThrow(() -> new IllegalStateException("Certificate font not found: " + resource));
    }

    private void drawCentered(PDPageContentStream content, PDType0Font font, float fontSize, Color color, String text, float y) throws IOException {
        float textWidth = font.getStringWidth(text) / 1000 * fontSize;
        float x = (842 - textWidth) / 2;

        content.beginText();
        content.setFont(font, fontSize);
        content.setNonStrokingColor(color);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }
}
