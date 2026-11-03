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
    SYS_METHOD_NOT_ALLOWED("LRN-SYS-405", HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    SYS_UNSUPPORTED_MEDIA_TYPE(
            "LRN-SYS-415", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
    VAL_FAILED("LRN-VAL-001", HttpStatus.BAD_REQUEST, "Validation failed"),
    VAL_MALFORMED_BODY("LRN-VAL-002", HttpStatus.BAD_REQUEST, "Malformed request body"),
    AUTH_UNAUTHENTICATED("LRN-AUTH-001", HttpStatus.UNAUTHORIZED, "Authentication required"),
    AUTH_INVALID_CREDENTIALS("LRN-AUTH-002", HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    AUTH_FORBIDDEN("LRN-AUTH-003", HttpStatus.FORBIDDEN, "Access denied"),
    AUTH_ACCOUNT_TOKEN_INVALID(
            "LRN-AUTH-004", HttpStatus.BAD_REQUEST, "This link is invalid or has expired"),
    AUTH_ACCOUNT_INACTIVE("LRN-AUTH-005", HttpStatus.FORBIDDEN, "This account is inactive"),
    USR_EMAIL_TAKEN("LRN-USR-001", HttpStatus.CONFLICT, "Email is already registered"),
    USR_NOT_FOUND("LRN-USR-002", HttpStatus.NOT_FOUND, "User not found"),
    USR_USERNAME_INVALID("LRN-USR-003", HttpStatus.BAD_REQUEST, "Username is unavailable or invalid"),
    CTR_BATCH_NOT_FOUND("LRN-CTR-001", HttpStatus.NOT_FOUND, "Batch not found"),
    CTR_ROSTER_NOT_FOUND("LRN-CTR-002", HttpStatus.NOT_FOUND, "Roster entry not found"),
    MAT_INVALID_FILE(
            "LRN-MAT-001", HttpStatus.BAD_REQUEST, "The uploaded file is not a valid PDF"),
    MAT_NOT_FOUND("LRN-MAT-002", HttpStatus.NOT_FOUND, "Material not found"),
    MAT_TOO_LARGE(
            "LRN-MAT-003", HttpStatus.PAYLOAD_TOO_LARGE, "The uploaded file is too large"),
    GEN_NOT_GENERATABLE(
            "LRN-GEN-001",
            HttpStatus.UNPROCESSABLE_CONTENT,
            "This material's text could not be extracted well enough to generate questions"),
    GEN_TOPIC_MISMATCH(
            "LRN-GEN-002",
            HttpStatus.BAD_REQUEST,
            "Topic does not belong to this material's grade and subject"),
    GEN_COUNT_INVALID(
            "LRN-GEN-003",
            HttpStatus.BAD_REQUEST,
            "Question counts must total between 1 and 40"),
    GEN_DAILY_LIMIT(
            "LRN-GEN-004", HttpStatus.TOO_MANY_REQUESTS, "Daily generation limit reached"),
    GEN_JOB_NOT_FOUND("LRN-GEN-005", HttpStatus.NOT_FOUND, "Generation job not found"),
    QST_IMMUTABLE("LRN-QST-001", HttpStatus.CONFLICT, "Only drafts can be changed; fork an approved question"),
    QST_NOT_FOUND("LRN-QST-002", HttpStatus.NOT_FOUND, "Question not found"),
    QST_INVALID("LRN-QST-003", HttpStatus.UNPROCESSABLE_CONTENT, "Question validation failed"),
    QST_STALE("LRN-QST-004", HttpStatus.CONFLICT, "This question changed; reload before trying again"),
    TST_INVALID("LRN-TST-001", HttpStatus.BAD_REQUEST, "Invalid test definition"),
    TST_PUBLISHED("LRN-TST-002", HttpStatus.CONFLICT, "A published definition cannot be deleted"),
    TST_PUBLISH_INVALID("LRN-TST-003", HttpStatus.BAD_REQUEST, "Invalid publish or instance operation"),
    TST_NOT_FOUND("LRN-TST-004", HttpStatus.NOT_FOUND, "Test or instance not found"),
    ATT_NOT_ASSIGNED("LRN-ATT-001", HttpStatus.NOT_FOUND, "Test or attempt not found"),
    ATT_NOT_AVAILABLE("LRN-ATT-002", HttpStatus.CONFLICT, "This test is not currently available"),
    ATT_EXHAUSTED("LRN-ATT-003", HttpStatus.CONFLICT, "No attempts remain");

    private final String code;
    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(String code, HttpStatus status, String defaultMessage) {
        this.code = code;
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
