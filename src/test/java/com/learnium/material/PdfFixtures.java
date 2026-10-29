package com.learnium.material;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Builds real PDF documents in test code with PDFBox, so extraction tests run
 * the production parser over known content instead of relying on binary
 * fixture files. Each list element becomes one page; each string is one line
 * of Helvetica text on that page. An empty list element makes a blank page.
 */
final class PdfFixtures {

    private PdfFixtures() {}

    static byte[] pdfOf(List<List<String>> pages) {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (List<String> lines : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (!lines.isEmpty()) {
                    try (PDPageContentStream content =
                            new PDPageContentStream(document, page)) {
                        content.beginText();
                        content.setFont(
                                new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                        content.newLineAtOffset(50, 750);
                        for (String line : lines) {
                            content.showText(line);
                            content.newLineAtOffset(0, -16);
                        }
                        content.endText();
                    }
                }
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("fixture generation failed", e);
        }
    }

    static byte[] encryptedPdf() {
        byte[] plain = pdfOf(List.of(List.of("Secret chapter content.")));
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(plain);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.protect(
                    new StandardProtectionPolicy("owner-pw", "user-pw", new AccessPermission()));
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("fixture generation failed", e);
        }
    }
}
