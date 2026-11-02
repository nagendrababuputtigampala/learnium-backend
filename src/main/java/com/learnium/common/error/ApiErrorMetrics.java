package com.learnium.common.error;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Bounded labels only: registered error code and HTTP status, never user input or IDs. */
@Component
public class ApiErrorMetrics {
    private final MeterRegistry registry;

    public ApiErrorMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(ErrorCode errorCode) {
        registry.counter("learnium.api.errors", "code", errorCode.getCode(),
                "status", Integer.toString(errorCode.getStatus().value())).increment();
    }
}
