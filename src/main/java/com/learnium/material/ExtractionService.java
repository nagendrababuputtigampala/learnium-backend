package com.learnium.material;

import com.learnium.model.content.ExtractionStatus;
import com.learnium.security.TokenHashing;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Turns source material into classified text. PDFs are read with PDFBox one
 * page at a time; pasted text skips parsing but goes through the same
 * classification. Classification separates extraction success from USABLE
 * text (see {@link ExtractionStatus}): page count and file size alone say
 * nothing about whether a chapter can feed generation.
 *
 * <p>Two bounds hold regardless of the document:
 *
 * <ul>
 *   <li><b>Time.</b> Parsing runs on a dedicated daemon executor and the
 *       caller waits at most the configured budget for it. A per-page check
 *       alone cannot bound the work — loading the document or stripping one
 *       pathological page can outlast any number of checks — so the future's
 *       deadline is the real bound: on timeout the task is cancelled and
 *       abandoned (the daemon thread cannot stall the caller or the JVM).
 *   <li><b>Memory.</b> Text accumulates through a bounded sink that retains
 *       at most the storage cap; the rest streams past — counted and hashed,
 *       never kept.
 * </ul>
 *
 * <p>Nothing about the content is logged — failures record the exception
 * class only.
 */
@Service
public class ExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);

    private final ExtractionProperties properties;
    private final ExecutorService executor;
    private final LongSupplier nanoClock;

    @Autowired
    public ExtractionService(
            ExtractionProperties properties,
            @Qualifier("extractionExecutor") ExecutorService executor) {
        this(properties, executor, System::nanoTime);
    }

    /** Convenience constructor (unit tests): parses run on a private pool. */
    public ExtractionService(ExtractionProperties properties) {
        this(properties, defaultExecutor(), System::nanoTime);
    }

    ExtractionService(
            ExtractionProperties properties, ExecutorService executor, LongSupplier nanoClock) {
        this.properties = properties;
        this.executor = executor;
        this.nanoClock = nanoClock;
    }

    /** Daemon pool shared by the Spring bean (ExtractionConfig) and tests. */
    static ExecutorService defaultExecutor() {
        ThreadFactory factory =
                task -> {
                    Thread thread = new Thread(task, "pdf-extraction");
                    thread.setDaemon(true);
                    return thread;
                };
        return Executors.newCachedThreadPool(factory);
    }

    /** Extracts and classifies the text of one PDF document. */
    public ExtractionResult extractPdf(byte[] pdfBytes) {
        int timeoutSeconds = properties.getTimeoutSeconds();
        if (timeoutSeconds <= 0) {
            // A non-positive budget is exhausted before any work starts.
            log.warn("PDF extraction exceeded its time budget");
            return ExtractionResult.failed();
        }
        Future<ExtractionResult> future = executor.submit(() -> extractWithinBudget(pdfBytes));
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("PDF extraction exceeded its time budget");
            return ExtractionResult.failed();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            log.warn("PDF extraction interrupted while waiting for the parser");
            return ExtractionResult.failed();
        } catch (ExecutionException e) {
            log.warn("PDF extraction failed: {}", e.getCause().getClass().getSimpleName());
            return ExtractionResult.failed();
        }
    }

    /**
     * The bounded work behind {@link #extractPdf}: load, page-cap check,
     * per-page stripping into the bounded sink, classification. Never throws
     * for a bad document — failures are classified. Package-private as the
     * test seam for the timeout behaviour of the wrapper.
     */
    ExtractionResult extractWithinBudget(byte[] pdfBytes) {
        long startNanos = nanoClock.getAsLong();
        long budgetNanos = TimeUnit.SECONDS.toNanos(properties.getTimeoutSeconds());
        TextSink sink = new TextSink(properties.getMaxExtractedChars());
        int pageCount;
        boolean overPageLimit = false;
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            // Loading a pathological document can itself burn the budget;
            // re-check before doing anything else with it.
            checkBudget(startNanos, budgetNanos);
            pageCount = document.getNumberOfPages();
            if (pageCount > properties.getMaxPages()) {
                // Rejected BEFORE any text is extracted: no partial text,
                // no hash, no character count is recorded for it.
                overPageLimit = true;
            } else {
                PDFTextStripper stripper = new PDFTextStripper();
                for (int page = 1; page <= pageCount; page++) {
                    checkBudget(startNanos, budgetNanos);
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    sink.append(stripper.getText(document));
                }
            }
        } catch (ExtractionTimeoutException e) {
            log.warn("PDF extraction exceeded its time budget");
            return ExtractionResult.failed();
        } catch (IOException | RuntimeException e) {
            log.warn("PDF extraction failed: {}", e.getClass().getSimpleName());
            return ExtractionResult.failed();
        }
        if (overPageLimit) {
            return ExtractionResult.overPageLimit();
        }
        return classify(sink, pageCount);
    }

    /** Classifies pasted text (no parsing; caps and usability still apply). */
    public ExtractionResult extractText(String text) {
        TextSink sink = new TextSink(properties.getMaxExtractedChars());
        sink.append(text);
        return classify(sink, 1);
    }

    private void checkBudget(long startNanos, long budgetNanos) {
        if (nanoClock.getAsLong() - startNanos >= budgetNanos) {
            throw new ExtractionTimeoutException();
        }
    }

    private ExtractionResult classify(TextSink sink, int pageCount) {
        long chars = sink.totalChars();
        if (chars == 0 || !sink.sawNonWhitespace()) {
            // Whitespace-only output is no text: an image-only page can still
            // yield separator newlines from the stripper.
            return new ExtractionResult(ExtractionStatus.NO_TEXT, "", 0, null);
        }
        // Identity is the digest of the COMPLETE text (see TextSink); the
        // stored text is the capped prefix of it.
        String contentHash = sink.contentHashHex();
        String stored = sink.keptText();
        if (chars < properties.getMinChars()
                || (pageCount > 1
                        && (double) chars / pageCount < properties.getMinAvgCharsPerPage())
                || sink.alphanumericRatio() < properties.getMinAlphanumericRatio()) {
            return new ExtractionResult(
                    ExtractionStatus.UNUSABLE_TEXT, stored, stored.length(), contentHash);
        }
        if (chars > properties.getMaxExtractedChars()
                || chars / 4 > properties.getMaxInputTokens()) {
            return new ExtractionResult(
                    ExtractionStatus.OVER_LIMIT, stored, stored.length(), contentHash);
        }
        return new ExtractionResult(ExtractionStatus.OK, stored, stored.length(), contentHash);
    }

    /**
     * Bounded accumulation for extracted text. Every chunk is digested
     * (SHA-256 over the complete text, so the content identity never depends
     * on the truncated store) and counted, but at most {@code cap} characters
     * are retained — beyond that, pages stream past with at most the current
     * page's own string held by the parser. Truncation therefore applies to
     * every persisted outcome, not just the well-behaved ones.
     */
    static final class TextSink {

        private final int cap;
        private final StringBuilder kept = new StringBuilder();
        private final MessageDigest digest = TokenHashing.newSha256Digest();
        private long totalChars;
        private long alphanumericChars;
        private boolean sawNonWhitespace;

        TextSink(int cap) {
            this.cap = cap;
        }

        void append(CharSequence chunk) {
            String text = chunk.toString();
            digest.update(text.getBytes(StandardCharsets.UTF_8));
            totalChars += text.length();
            text.codePoints()
                    .forEach(
                            codePoint -> {
                                if (Character.isLetterOrDigit(codePoint)) {
                                    alphanumericChars++;
                                }
                                if (!Character.isWhitespace(codePoint)) {
                                    sawNonWhitespace = true;
                                }
                            });
            int room = cap - kept.length();
            if (room > 0) {
                kept.append(text, 0, Math.min(room, text.length()));
            }
        }

        long totalChars() {
            return totalChars;
        }

        boolean sawNonWhitespace() {
            return sawNonWhitespace;
        }

        double alphanumericRatio() {
            return (double) alphanumericChars / totalChars;
        }

        String keptText() {
            return kept.toString();
        }

        String contentHashHex() {
            return HexFormat.of().formatHex(digest.digest());
        }
    }

    /** Control-flow signal for the extraction time budget; never logged with content. */
    static final class ExtractionTimeoutException extends RuntimeException {}
}
