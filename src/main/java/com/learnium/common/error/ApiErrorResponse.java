package com.learnium.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 * Standard error body returned for every API failure:
 * {@code { code, message, traceId, timestamp }} plus per-field detail for validation
 * errors. {@code fieldErrors} is omitted from the JSON when not set.
 */
@Getter
@Builder
public class ApiErrorResponse {

    private final String code;
    private final String message;
    private final String traceId;
    private final Instant timestamp;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Map<String, String> fieldErrors;
}
