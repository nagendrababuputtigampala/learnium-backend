package com.learnium.model.content;

/**
 * Outcome of running text extraction over a material's source. Extraction
 * success and usable text are different things: a scanned chapter can yield
 * zero characters (NO_TEXT) or a nonempty but useless trickle (a selectable
 * header on otherwise imaged pages — UNUSABLE_TEXT), and a very large
 * chapter is stored truncated at the configured cap (OVER_LIMIT). Only OK
 * and OVER_LIMIT materials carry text generation may read; the status is
 * recorded on the material either way so the teacher sees WHY a chapter
 * cannot be used as-is.
 */
public enum ExtractionStatus {
    /** Text extracted and within all caps. */
    OK,
    /** No extractable text at all (scanned / image-only PDF). */
    NO_TEXT,
    /** Some text, but below the usability thresholds. */
    UNUSABLE_TEXT,
    /** Text exceeded the caps; stored truncated at the character cap. */
    OVER_LIMIT,
    /** The parser failed (corrupt, encrypted or unreadable input). */
    EXTRACTION_FAILED
}
