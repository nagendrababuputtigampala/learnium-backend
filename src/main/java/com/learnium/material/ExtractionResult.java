package com.learnium.material;

import com.learnium.model.content.ExtractionStatus;
import com.learnium.security.TokenHashing;

/**
 * The classified outcome of one extraction.
 *
 * @param status the extraction outcome
 * @param storedText the text to persist — already truncated at the storage
 *     cap for every outcome that carries text; {@code null} when the page cap
 *     rejected the document before any text was extracted
 * @param storedChars length of {@code storedText}; {@code null} together with
 *     it on the page-cap outcome
 * @param contentHash SHA-256 hex of the COMPLETE extracted text (computed
 *     while streaming, before any truncation — two chapters that share a
 *     long prefix but differ later have different identities); {@code null}
 *     when extraction produced no text at all, so two scanned PDFs never
 *     share an identity
 */
public record ExtractionResult(
        ExtractionStatus status, String storedText, Integer storedChars, String contentHash) {

    /**
     * Convenience for hand-built results: the hash is derived from the stored
     * text. Production results come from {@link ExtractionService}, whose
     * hash covers the complete text, not the truncated store.
     */
    public ExtractionResult(ExtractionStatus status, String storedText, Integer storedChars) {
        this(
                status,
                storedText,
                storedChars,
                storedText == null || storedText.isBlank()
                        ? null
                        : TokenHashing.sha256Hex(storedText));
    }

    static ExtractionResult failed() {
        return new ExtractionResult(ExtractionStatus.EXTRACTION_FAILED, "", 0, null);
    }

    /**
     * The page cap rejected the document before extraction ran: no partial
     * work is recorded — no text, no character count, no hash.
     */
    static ExtractionResult overPageLimit() {
        return new ExtractionResult(ExtractionStatus.OVER_LIMIT, null, null, null);
    }
}
