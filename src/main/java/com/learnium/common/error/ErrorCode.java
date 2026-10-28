package com.learnium.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Registry of stable API error codes.
 *
 * <p>Codes follow the {@code LRN-<DOMAIN>-<NNN>} scheme and are part of the public API
 * contract: clients, support and alerting key off them, so a code's meaning never changes
 * once shipped. Domain prefixes: SYS (platform), VAL (validation), AUTH (authentication /
 * authorization). Feature domains add their own prefixes as they land.
 */
@Getter
public enum ErrorCode {

    SYS_UNEXPECTED("LRN-SYS-001", HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error"),
    SYS_NOT_FOUND("LRN-SYS-404", HttpStatus.NOT_FOUND, "Resource not found"),
    VAL_FAILED("LRN-VAL-001", HttpStatus.BAD_REQUEST, "Validation failed"),
    AUTH_UNAUTHENTICATED("LRN-AUTH-001", HttpStatus.UNAUTHORIZED, "Authentication required"),
    AUTH_FORBIDDEN("LRN-AUTH-003", HttpStatus.FORBIDDEN, "Access denied");

    private final String code;
    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(String code, HttpStatus status, String defaultMessage) {
        this.code = code;
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
