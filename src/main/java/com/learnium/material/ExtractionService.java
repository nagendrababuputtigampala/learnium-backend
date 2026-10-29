package com.learnium.material;

import com.learnium.model.content.ExtractionStatus;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Turns source material into classified text. PDFs are read with PDFBox one
 * page at a time under a wall-clock budget; pasted text skips parsing but
 * goes through the same classification. Classification separates extraction
 * success from USABLE text (see {@link ExtractionStatus}): page count and
 * file size alone say nothing about whether a chapter can feed generation.
 *
 * <p>Nothing about the content is logged — failures record the exception
 * class only.
 */
@Service
public class ExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);

    private final ExtractionProperties properties;

    public ExtractionService(ExtractionProperties properties) {
        this.properties = properties;
    }

    /** Extracts and classifies the text of one PDF document. */
    public ExtractionResult extractPdf(byte[] pdfBytes) {
        long startNanos = System.nanoTime();
        long budgetNanos = TimeUnit.SECONDS.toNanos(properties.getTimeoutSeconds());
        StringBuilder text = new StringBuilder();
        int pageCount;
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            pageCount = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= pageCount; page++) {
                checkBudget(startNanos, budgetNanos);
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                text.append(stripper.getText(document));
            }
        } catch (ExtractionTimeoutException e) {
            log.warn("PDF extraction exceeded its time budget");
            return ExtractionResult.failed();
        } catch (IOException | RuntimeException e) {
            log.warn("PDF extraction failed: {}", e.getClass().getSimpleName());
            return ExtractionResult.failed();
        }
        return classify(text.toString(), pageCount);
    }

    /** Classifies pasted text (no parsing; caps and usability still apply). */
    public ExtractionResult extractText(String text) {
        return classify(text, 1);
    }

    private void checkBudget(long startNanos, long budgetNanos) {
        if (System.nanoTime() - startNanos >= budgetNanos) {
            throw new ExtractionTimeoutException();
        }
    }

    private ExtractionResult classify(String text, int pageCount) {
        int chars = text.length();
        if (chars == 0 || text.isBlank()) {
            // Whitespace-only output is no text: an image-only page can still
            // yield separator newlines from the stripper.
            return new ExtractionResult(ExtractionStatus.NO_TEXT, "", 0);
        }
        if (chars < properties.getMinChars()
                || (pageCount > 1
                        && (double) chars / pageCount < properties.getMinAvgCharsPerPage())
                || alphanumericRatio(text) < properties.getMinAlphanumericRatio()) {
            return new ExtractionResult(ExtractionStatus.UNUSABLE_TEXT, text, chars);
        }
        if (chars > properties.getMaxExtractedChars()
                || chars / 4 > properties.getMaxInputTokens()) {
            String stored = text.substring(0, Math.min(chars, properties.getMaxExtractedChars()));
            return new ExtractionResult(ExtractionStatus.OVER_LIMIT, stored, stored.length());
        }
        return new ExtractionResult(ExtractionStatus.OK, text, chars);
    }

    private static double alphanumericRatio(String text) {
        long alnum = text.chars().filter(Character::isLetterOrDigit).count();
        return (double) alnum / text.length();
    }

    /** Control-flow signal for the extraction time budget; never logged with content. */
    private static final class ExtractionTimeoutException extends RuntimeException {}
}
