package com.learnium.common.error;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Single translation point from exceptions to the standard {@link ApiErrorResponse} body.
 * Business errors keep their {@link ErrorCode}; anything unexpected collapses to
 * {@code LRN-SYS-001} with a generic message — internal detail goes to the logs only.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String MDC_TRACE_ID = "traceId";
    private static final String TRACE_ID_FALLBACK = "unknown";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        if (errorCode.getStatus().is5xxServerError()) {
            log.error("Business error {}: {}", errorCode.getCode(), ex.getMessage(), ex);
        } else {
            log.warn("Business error {}: {}", errorCode.getCode(), ex.getMessage());
        }
        return buildResponse(errorCode, ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        log.warn("Validation error {}: {}", ErrorCode.VAL_FAILED.getCode(), fieldErrors);
        return buildResponse(ErrorCode.VAL_FAILED, ErrorCode.VAL_FAILED.getDefaultMessage(), fieldErrors);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFoundException(NoResourceFoundException ex) {
        log.warn("Not found {}: {}", ErrorCode.SYS_NOT_FOUND.getCode(), ex.getResourcePath());
        return buildResponse(ErrorCode.SYS_NOT_FOUND, ErrorCode.SYS_NOT_FOUND.getDefaultMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception ex) {
        log.error("Unexpected error {}: {}", ErrorCode.SYS_UNEXPECTED.getCode(), ex.getMessage(), ex);
        return buildResponse(
                ErrorCode.SYS_UNEXPECTED, ErrorCode.SYS_UNEXPECTED.getDefaultMessage(), null);
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            ErrorCode errorCode, String message, Map<String, String> fieldErrors) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .code(errorCode.getCode())
                .message(message)
                .traceId(currentTraceId())
                .timestamp(Instant.now())
                .fieldErrors(fieldErrors)
                .build();
        return ResponseEntity.status(errorCode.getStatus()).body(body);
    }

    private String currentTraceId() {
        String traceId = MDC.get(MDC_TRACE_ID);
        return traceId != null ? traceId : TRACE_ID_FALLBACK;
    }
}
