package com.learnium.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiErrorResponseTest {

    @Test
    void builderExposesAllFields() {
        Instant timestamp = Instant.parse("2026-10-28T00:00:00Z");
        Map<String, String> fieldErrors = Map.of("name", "must not be blank");

        ApiErrorResponse response = ApiErrorResponse.builder()
                .code("LRN-VAL-001")
                .message("Validation failed")
                .traceId("trace-123")
                .timestamp(timestamp)
                .fieldErrors(fieldErrors)
                .build();

        assertThat(response.getCode()).isEqualTo("LRN-VAL-001");
        assertThat(response.getMessage()).isEqualTo("Validation failed");
        assertThat(response.getTraceId()).isEqualTo("trace-123");
        assertThat(response.getTimestamp()).isEqualTo(timestamp);
        assertThat(response.getFieldErrors()).containsExactlyEntriesOf(fieldErrors);
    }

    @Test
    void fieldErrorsDefaultToNullSoTheyAreOmittedFromJson() {
        ApiErrorResponse response = ApiErrorResponse.builder()
                .code("LRN-SYS-001")
                .message("Unexpected server error")
                .traceId("trace-456")
                .timestamp(Instant.parse("2026-10-28T00:00:00Z"))
                .build();

        assertThat(response.getFieldErrors()).isNull();
    }
}
