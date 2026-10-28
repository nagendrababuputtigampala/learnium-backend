package com.learnium.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * With an OTLP endpoint configured, the context must wire the OpenTelemetry
 * tracing bridge and the OTLP meter registry (export itself is async and
 * fire-and-forget, so the unreachable localhost endpoint is fine).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "management.otlp.tracing.endpoint=http://localhost:4318/v1/traces",
            "management.otlp.metrics.export.url=http://localhost:4318/v1/metrics",
            // @SpringBootTest disables metrics export by default; opt this registry back in.
            "management.otlp.metrics.export.enabled=true"
        })
class ObservabilityConfigTest {

    @Autowired private MeterRegistry meterRegistry;
    @Autowired private Tracer tracer;

    @Test
    void otlpRegistryAndTracerAreWired() {
        assertThat(meterRegistry).isInstanceOf(OtlpMeterRegistry.class);
        assertThat(tracer).isNotNull();
        assertThat(tracer.currentSpan()).isNull(); // no span outside a request, as expected
    }
}
