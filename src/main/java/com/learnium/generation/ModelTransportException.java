package com.learnium.generation;

import lombok.Getter;

/**
 * A transport-level failure: an HTTP error status from the provider, or
 * an I/O problem reaching it ({@code statusCode} -1). Carries the status
 * only — never the response body or a message that could quote content.
 * Adapters translate this into a MODEL_ERROR outcome.
 */
@Getter
public class ModelTransportException extends RuntimeException {

    private final int statusCode;

    public ModelTransportException(int statusCode) {
        super("Model request failed (status " + statusCode + ")");
        this.statusCode = statusCode;
    }

    public ModelTransportException(int statusCode, Throwable cause) {
        super("Model request failed (status " + statusCode + ")", cause);
        this.statusCode = statusCode;
    }
}
