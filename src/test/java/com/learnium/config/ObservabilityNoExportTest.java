package com.learnium.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingConnectionDetails;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * With no OTLP endpoint configured (the default), no tracing connection
 * details — and therefore no span exporter — may be created, while the rest
 * of the context stays healthy.
 */
@SpringBootTest
@ActiveProfiles("test")
class ObservabilityNoExportTest {

    @Autowired private OtlpTracingProperties tracingProperties;
    @Autowired private ObjectProvider<OtlpTracingConnectionDetails> tracingConnectionDetails;
    @Autowired private MeterRegistry meterRegistry;

    @Test
    void noEndpointMeansNoExporterButAHealthyContext() {
        assertThat(tracingProperties.getEndpoint()).isNull();
        assertThat(tracingConnectionDetails.getIfAvailable()).isNull();
        assertThat(meterRegistry).isNotNull();
    }
}
