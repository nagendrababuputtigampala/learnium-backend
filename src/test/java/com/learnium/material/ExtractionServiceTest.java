package com.learnium.material;

import static org.assertj.core.api.Assertions.assertThat;

import com.learnium.model.content.ExtractionStatus;
import com.learnium.security.TokenHashing;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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
    void multiPageDocumentWithHealthyDensityIsOk() {
        // Exercises the per-page density check on its passing side: two
        // full pages must not be flagged just for being multi-page.
        String page =
                "Cell division is the process by which a parent cell divides into two "
                        + "daughter cells, each carrying a copy of the genetic material. "
                        + "Mitosis produces identical cells for growth and repair in the body. ";
        byte[] pdf =
                PdfFixtures.pdfOf(
                        List.of(linesOf(page.repeat(2)), linesOf(page.repeat(2))));

        ExtractionResult result = service.extractPdf(pdf);

        assertThat(result.status()).isEqualTo(ExtractionStatus.OK);
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
    void fortyOnePageDocumentIsRejectedByThePageCapBeforeExtraction() {
        ExtractionProperties properties = new ExtractionProperties();
        properties.setMaxPages(40);
        // Content-rich pages: the rejection must be the page cap itself,
        // not a usability side effect, and no partial work is recorded.
        List<List<String>> pages = new ArrayList<>();
        for (int i = 0; i < 41; i++) {
            pages.add(linesOf(PROSE));
        }

        ExtractionResult result =
                new ExtractionService(properties).extractPdf(PdfFixtures.pdfOf(pages));

        assertThat(result.status()).isEqualTo(ExtractionStatus.OVER_LIMIT);
        assertThat(result.storedText()).isNull();
        assertThat(result.storedChars()).isNull();
        assertThat(result.contentHash()).isNull();
    }

    @Test
    void documentAtThePageCapStillExtracts() {
        ExtractionProperties properties = new ExtractionProperties();
        properties.setMaxPages(40);
        List<List<String>> pages = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            pages.add(linesOf(PROSE));
        }

        ExtractionResult result =
                new ExtractionService(properties).extractPdf(PdfFixtures.pdfOf(pages));

        assertThat(result.status()).isEqualTo(ExtractionStatus.OK);
    }

    @Test
    void slowParseIsAbandonedAtTheBudget() {
        // One uninterruptible page must not hold the caller hostage: the
        // future deadline, not a per-page check, is the bound.
        ExtractionProperties properties = new ExtractionProperties();
        properties.setTimeoutSeconds(1);
        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            ExtractionService slow =
                    new ExtractionService(properties, executor) {
                        @Override
                        ExtractionResult extractWithinBudget(byte[] pdfBytes) {
                            try {
                                Thread.sleep(60_000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return new ExtractionResult(ExtractionStatus.OK, "late", 4);
                        }
                    };

            long startedNanos = System.nanoTime();
            ExtractionResult result = slow.extractPdf(new byte[0]);
            long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000;

            assertThat(result.status()).isEqualTo(ExtractionStatus.EXTRACTION_FAILED);
            assertThat(elapsedMillis).isLessThan(10_000);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void budgetExpiringDuringTheParseFailsExtraction() {
        // The clock jumps past the budget right after the start stamp, so
        // the post-load budget check inside the parse is what fires.
        ExtractionProperties properties = new ExtractionProperties();
        properties.setTimeoutSeconds(1);
        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            AtomicLong calls = new AtomicLong();
            ExtractionService service =
                    new ExtractionService(
                            properties,
                            executor,
                            () -> calls.getAndIncrement() == 0
                                    ? 0L
                                    : TimeUnit.SECONDS.toNanos(2));
            byte[] pdf = PdfFixtures.pdfOf(List.of(linesOf(PROSE)));

            ExtractionResult result = service.extractPdf(pdf);

            assertThat(result.status()).isEqualTo(ExtractionStatus.EXTRACTION_FAILED);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void parserCrashFailsExtraction() {
        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            ExtractionService broken =
                    new ExtractionService(new ExtractionProperties(), executor) {
                        @Override
                        ExtractionResult extractWithinBudget(byte[] pdfBytes) {
                            throw new IllegalStateException("parser exploded");
                        }
                    };

            assertThat(broken.extractPdf(new byte[0]).status())
                    .isEqualTo(ExtractionStatus.EXTRACTION_FAILED);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void interruptedWaitFailsExtractionAndRestoresTheFlag() {
        ExtractionProperties properties = new ExtractionProperties();
        properties.setTimeoutSeconds(30);
        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            ExtractionService slow =
                    new ExtractionService(properties, executor) {
                        @Override
                        ExtractionResult extractWithinBudget(byte[] pdfBytes) {
                            try {
                                Thread.sleep(60_000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return ExtractionResult.failed();
                        }
                    };

            Thread.currentThread().interrupt();
            ExtractionResult result = slow.extractPdf(new byte[0]);

            assertThat(result.status()).isEqualTo(ExtractionStatus.EXTRACTION_FAILED);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // never leak the flag into other tests
            executor.shutdownNow();
        }
    }

    @Test
    void unusableTextIsTruncatedAtTheStorageCapToo() {
        ExtractionProperties properties = new ExtractionProperties();
        properties.setMaxExtractedChars(50);

        ExtractionResult result =
                new ExtractionService(properties).extractText("!@# $%^ &*() ".repeat(40));

        assertThat(result.status()).isEqualTo(ExtractionStatus.UNUSABLE_TEXT);
        assertThat(result.storedText()).hasSize(50);
        assertThat(result.storedChars()).isEqualTo(50);
    }

    @Test
    void cappedPdfStoresOnlyTheCapButHashesTheWholeDocument() {
        byte[] pdf =
                PdfFixtures.pdfOf(
                        List.of(
                                linesOf(PROSE.repeat(2)),
                                linesOf(PROSE.repeat(2)),
                                linesOf(PROSE.repeat(2))));
        ExtractionResult whole = service.extractPdf(pdf);

        ExtractionProperties properties = new ExtractionProperties();
        properties.setMaxExtractedChars(100);
        properties.setMaxInputTokens(1_000_000);
        ExtractionResult capped = new ExtractionService(properties).extractPdf(pdf);

        assertThat(whole.status()).isEqualTo(ExtractionStatus.OK);
        assertThat(capped.status()).isEqualTo(ExtractionStatus.OVER_LIMIT);
        assertThat(capped.storedChars()).isEqualTo(100);
        // The identity digests every page, including the ones past the cap.
        assertThat(capped.contentHash()).isEqualTo(whole.contentHash());
    }

    @Test
    void hashCoversTheCompleteTextNotTheTruncatedStore() {
        ExtractionProperties properties = new ExtractionProperties();
        properties.setMaxExtractedChars(100);
        properties.setMaxInputTokens(1_000_000);
        ExtractionService capped = new ExtractionService(properties);
        // Both texts share a 150-char prefix — well past the 100-char store —
        // and differ only afterwards.
        String prefix = "word ".repeat(30);
        String first = prefix + "alpha ".repeat(50);
        String second = prefix + "beta ".repeat(50);

        ExtractionResult a = capped.extractText(first);
        ExtractionResult b = capped.extractText(second);

        assertThat(a.storedText()).isEqualTo(b.storedText());
        assertThat(a.contentHash()).isEqualTo(TokenHashing.sha256Hex(first));
        assertThat(b.contentHash()).isEqualTo(TokenHashing.sha256Hex(second));
        assertThat(a.contentHash()).isNotEqualTo(b.contentHash());
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
