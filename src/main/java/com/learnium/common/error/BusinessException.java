package com.learnium.common.error;

import lombok.Getter;

/**
 * Exception for expected, domain-level failures. Carries an {@link ErrorCode}; the
 * {@link GlobalExceptionHandler} translates it into the standard API error body.
 * Unexpected technical failures should not use this type — they surface as
 * {@code LRN-SYS-001} via the catch-all handler.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final java.util.Map<String, String> fieldErrors;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BusinessException(ErrorCode errorCode, String message, java.util.Map<String, String> fieldErrors) {
        super(message);
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors;
    }
}
