package com.learnium.common.error;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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

    /**
     * Method-security denials ({@code @PreAuthorize}) surface inside the
     * DispatcherServlet, past the security filter chain, so the chain's
     * AccessDeniedHandler never sees them — translate them here to the same
     * {@code LRN-AUTH-003} envelope. Filter-level denials are handled by
     * {@code ApiAccessDeniedHandler} before dispatch and never reach this advice.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied {}: {}", ErrorCode.AUTH_FORBIDDEN.getCode(), ex.getMessage());
        return buildResponse(
                ErrorCode.AUTH_FORBIDDEN, ErrorCode.AUTH_FORBIDDEN.getDefaultMessage(), null);
    }

    /**
     * Framework request-decoding failures are client errors, not server faults:
     * they keep their proper HTTP status and a stable code instead of collapsing
     * into {@code LRN-SYS-001}. Logged at WARN without a stack trace — a broken
     * client body is routine noise, not an incident.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedBody(HttpMessageNotReadableException ex) {
        // NEVER log ex.getMessage() here: parser messages can quote the submitted
        // body (field values, tokens), which would leak client input into the logs.
        // Code, trace id and the exception type are enough to diagnose the failure.
        log.warn(
                "Malformed body {} (traceId={}, cause={})",
                ErrorCode.VAL_MALFORMED_BODY.getCode(),
                currentTraceId(),
                ex.getClass().getSimpleName());
        return buildResponse(
                ErrorCode.VAL_MALFORMED_BODY, ErrorCode.VAL_MALFORMED_BODY.getDefaultMessage(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        log.warn(
                "Method not allowed {}: {} (supported: {})",
                ErrorCode.SYS_METHOD_NOT_ALLOWED.getCode(),
                ex.getMethod(),
                ex.getSupportedHttpMethods());
        // Preserve the framework headers (Allow: <supported methods>) on the 405.
        return buildResponse(
                ErrorCode.SYS_METHOD_NOT_ALLOWED,
                ErrorCode.SYS_METHOD_NOT_ALLOWED.getDefaultMessage(),
                null,
                ex.getHeaders());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex) {
        log.warn(
                "Unsupported media type {}: {}",
                ErrorCode.SYS_UNSUPPORTED_MEDIA_TYPE.getCode(),
                ex.getContentType());
        return buildResponse(
                ErrorCode.SYS_UNSUPPORTED_MEDIA_TYPE,
                ErrorCode.SYS_UNSUPPORTED_MEDIA_TYPE.getDefaultMessage(),
                null);
    }

    /**
     * An upload beyond the servlet multipart cap never reaches the
     * controller; translate it into the materials domain code so clients
     * see the same envelope as a service-level size rejection. The file
     * name and content are never logged.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn(
                "Upload too large {} (max {} bytes)",
                ErrorCode.MAT_TOO_LARGE.getCode(),
                ex.getMaxUploadSize());
        return buildResponse(
                ErrorCode.MAT_TOO_LARGE, ErrorCode.MAT_TOO_LARGE.getDefaultMessage(), null);
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
        return buildResponse(errorCode, message, fieldErrors, null);
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            ErrorCode errorCode,
            String message,
            Map<String, String> fieldErrors,
            HttpHeaders headers) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .code(errorCode.getCode())
                .message(message)
                .traceId(currentTraceId())
                .timestamp(Instant.now())
                .fieldErrors(fieldErrors)
                .build();
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(errorCode.getStatus());
        if (headers != null) {
            responseBuilder.headers(headers);
        }
        return responseBuilder.body(body);
    }

    private String currentTraceId() {
        String traceId = MDC.get(MDC_TRACE_ID);
        return traceId != null ? traceId : TRACE_ID_FALLBACK;
    }
}
