package com.learnium.material;

import static org.assertj.core.api.Assertions.assertThat;

import com.learnium.model.content.ExtractionStatus;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtractionServiceTest {

    private static final String PROSE =
            "Fractions represent parts of a whole. When the numerator and the denominator "
                    + "share a common factor, the fraction can be simplified by dividing both "
                    + "by that factor. Equivalent fractions name the same value in different "
                    + "forms, which is why one half equals two quarters and three sixths. ";

    private final ExtractionService service = new ExtractionService(new ExtractionProperties());

    @Test
    void textPdfExtractsAsOk() {
        byte[] pdf = PdfFixtures.pdfOf(List.of(linesOf(PROSE.repeat(3))));

        ExtractionResult result = service.extractPdf(pdf);

        assertThat(result.status()).isEqualTo(ExtractionStatus.OK);
        assertThat(result.storedText().replaceAll("\\s+", " "))
                .contains("Fractions represent parts of a whole");
        assertThat(result.storedChars()).isEqualTo(result.storedText().length());
    }

    @Test
    void blankPagesAreNoText() {
        byte[] pdf = PdfFixtures.pdfOf(List.of(List.of(), List.of()));

        ExtractionResult result = service.extractPdf(pdf);

        assertThat(result.status()).isEqualTo(ExtractionStatus.NO_TEXT);
        assertThat(result.storedText()).isEmpty();
        assertThat(result.storedChars()).isZero();
    }

    @Test
    void headerOnlyPagesAreUnusable() {
        // A scanned chapter with a selectable header: some text, far too
        // little to be a source.
        byte[] pdf =
                PdfFixtures.pdfOf(
                        List.of(List.of("Chapter 4"), List.of("Chapter 4"), List.of("Chapter 4")));

        ExtractionResult result = service.extractPdf(pdf);

        assertThat(result.status()).isEqualTo(ExtractionStatus.UNUSABLE_TEXT);
        assertThat(result.storedText()).contains("Chapter 4");
    }

    @Test
    void spreadThinMultiPageTextIsUnusableByAverage() {
        ExtractionProperties properties = new ExtractionProperties();
        properties.setMinChars(100);
        properties.setMinAvgCharsPerPage(50);
        // 5 pages x ~30 real characters: total passes minChars, average fails.
        List<List<String>> pages =
                List.of(
                        List.of("Apples and oranges are fruits"),
                        List.of("Apples and oranges are fruits"),
                        List.of("Apples and oranges are fruits"),
                        List.of("Apples and oranges are fruits"),
                        List.of("Apples and oranges are fruits"));

        ExtractionResult result = new ExtractionService(properties).extractPdf(PdfFixtures.pdfOf(pages));

        assertThat(result.status()).isEqualTo(ExtractionStatus.UNUSABLE_TEXT);
    }

    @Test
    void symbolOnlyTextIsUnusableByRatio() {
        ExtractionResult result = service.extractText("!@# $%^ &*() ".repeat(40));

        assertThat(result.status()).isEqualTo(ExtractionStatus.UNUSABLE_TEXT);
    }

    @Test
    void randomBytesFailExtraction() {
        ExtractionResult result =
                service.extractPdf("not a pdf at all".getBytes(StandardCharsets.UTF_8));

        assertThat(result.status()).isEqualTo(ExtractionStatus.EXTRACTION_FAILED);
        assertThat(result.storedText()).isEmpty();
    }

    @Test
    void corruptPdfBodyFailsExtraction() {
        byte[] bytes = "%PDF-1.7 garbage body with no objects".getBytes(StandardCharsets.UTF_8);

        ExtractionResult result = service.extractPdf(bytes);

        assertThat(result.status()).isEqualTo(ExtractionStatus.EXTRACTION_FAILED);
    }

    @Test
    void encryptedPdfFailsExtraction() {
        ExtractionResult result = service.extractPdf(PdfFixtures.encryptedPdf());

        assertThat(result.status()).isEqualTo(ExtractionStatus.EXTRACTION_FAILED);
    }

    @Test
    void exhaustedTimeBudgetFailsExtraction() {
        ExtractionProperties properties = new ExtractionProperties();
        properties.setTimeoutSeconds(0);
        byte[] pdf = PdfFixtures.pdfOf(List.of(linesOf(PROSE)));

        ExtractionResult result = new ExtractionService(properties).extractPdf(pdf);

        assertThat(result.status()).isEqualTo(ExtractionStatus.EXTRACTION_FAILED);
    }

    @Test
    void charCapTruncatesAndMarksOverLimit() {
        ExtractionProperties properties = new ExtractionProperties();
        properties.setMaxExtractedChars(100);
        properties.setMaxInputTokens(1_000_000);

        ExtractionResult result =
                new ExtractionService(properties).extractText(PROSE.repeat(3));

        assertThat(result.status()).isEqualTo(ExtractionStatus.OVER_LIMIT);
        assertThat(result.storedChars()).isEqualTo(100);
        assertThat(result.storedText()).hasSize(100);
    }

    @Test
    void tokenCapMarksOverLimitWithoutTruncatingBelowTheCharCap() {
        ExtractionProperties properties = new ExtractionProperties();
        properties.setMaxExtractedChars(1_000_000);
        properties.setMaxInputTokens(10);

        ExtractionResult result =
                new ExtractionService(properties).extractText(PROSE.repeat(3));

        assertThat(result.status()).isEqualTo(ExtractionStatus.OVER_LIMIT);
        assertThat(result.storedText()).isEqualTo(PROSE.repeat(3));
    }

    @Test
    void pasteTextClassifiesLikePdfText() {
        assertThat(service.extractText(PROSE.repeat(3)).status()).isEqualTo(ExtractionStatus.OK);
        assertThat(service.extractText("too short").status())
                .isEqualTo(ExtractionStatus.UNUSABLE_TEXT);
        assertThat(service.extractText("").status()).isEqualTo(ExtractionStatus.NO_TEXT);
        assertThat(service.extractText("   \n  ").status()).isEqualTo(ExtractionStatus.NO_TEXT);
    }

    /** Splits a long string into showable lines of at most 78 characters. */
    private static List<String> linesOf(String text) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (int i = 0; i < text.length(); i += 78) {
            lines.add(text.substring(i, Math.min(text.length(), i + 78)));
        }
        return lines;
    }
}
