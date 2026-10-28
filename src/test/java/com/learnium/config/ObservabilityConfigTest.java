package com.learnium.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingConnectionDetails;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingProperties;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.Transport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Proves the Boot 4 OTLP properties actually BIND — not merely that beans
 * exist. The tracing endpoint must land on {@link OtlpTracingProperties} and
 * produce an {@link OtlpTracingConnectionDetails} pointing at the same URL
 * (the exporter auto-configuration only creates that bean when
 * {@code management.opentelemetry.tracing.export.otlp.endpoint} is set; the
 * pre-4 {@code management.otlp.tracing.endpoint} name binds nothing and this
 * test would fail). Export itself is async, so the unreachable localhost
 * endpoint is fine and no network call is made here.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "management.opentelemetry.tracing.export.otlp.endpoint=http://localhost:4318/v1/traces",
            "management.otlp.metrics.export.url=http://localhost:4318/v1/metrics",
            // @SpringBootTest disables metrics export by default; opt this registry back in.
            "management.otlp.metrics.export.enabled=true"
        })
class ObservabilityConfigTest {

    @Autowired private MeterRegistry meterRegistry;
    @Autowired private Tracer tracer;
    @Autowired private OtlpTracingProperties tracingProperties;
    @Autowired private OtlpTracingConnectionDetails tracingConnectionDetails;
    @Autowired private OtlpConfig otlpConfig;

    @Test
    void tracingEndpointBindsToTheBoot4Property() {
        assertThat(tracingProperties.getEndpoint()).isEqualTo("http://localhost:4318/v1/traces");
        assertThat(tracingConnectionDetails.getUrl(Transport.HTTP))
                .isEqualTo("http://localhost:4318/v1/traces");
    }

    @Test
    void metricsEndpointBindsAndRegistriesAreWired() {
        assertThat(otlpConfig.url()).isEqualTo("http://localhost:4318/v1/metrics");
        assertThat(meterRegistry).isInstanceOf(OtlpMeterRegistry.class);
        assertThat(tracer).isNotNull();
        assertThat(tracer.currentSpan()).isNull(); // no span outside a request, as expected
    }
}
