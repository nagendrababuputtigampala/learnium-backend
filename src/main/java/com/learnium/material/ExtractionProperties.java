package com.learnium.material;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Extraction limits and usability thresholds ({@code learnium.extraction.*}).
 * The caps bound what generation may later spend on one chapter: extracted
 * characters and an estimated input-token count (chars / 4). The thresholds
 * separate "some text" from "usable text" — a scanned chapter with a
 * selectable header must not pass as a source.
 */
@ConfigurationProperties(prefix = "learnium.extraction")
@Getter
@Setter
public class ExtractionProperties {

    /** Wall-clock budget for one PDF extraction, in seconds. */
    private int timeoutSeconds = 30;

    /** Stored extracted text is truncated at this many characters. */
    private int maxExtractedChars = 200_000;

    /** Estimated input-token cap (estimated as characters / 4). */
    private int maxInputTokens = 40_000;

    /** Below this many characters, extracted text is unusable. */
    private int minChars = 200;

    /** For multi-page PDFs, below this average characters per page is unusable. */
    private int minAvgCharsPerPage = 50;

    /** Below this letter/digit share of the text, it is unusable. */
    private double minAlphanumericRatio = 0.5;
}
