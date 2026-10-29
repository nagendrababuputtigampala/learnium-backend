package com.learnium.material;

import com.learnium.model.content.ExtractionStatus;

/**
 * The classified outcome of one extraction: the status plus the text to
 * store (already truncated when OVER_LIMIT) and its length. On
 * EXTRACTION_FAILED both text fields are empty — a failed parse stores no
 * partial text.
 */
public record ExtractionResult(ExtractionStatus status, String storedText, int storedChars) {

    static ExtractionResult failed() {
        return new ExtractionResult(ExtractionStatus.EXTRACTION_FAILED, "", 0);
    }
}
