package com.learnium.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.test.context.ActiveProfiles;

/**
 * Guards the spring.factories registration of
 * {@link OtelEndpointEnvironmentPostProcessor}: without
 * {@code OTEL_EXPORTER_OTLP_ENDPOINT} in the environment, a real context must
 * carry its property source, expose no tracing endpoint, and have OTLP
 * metrics export switched off. (The endpoint-set direction is covered by the
 * processor's unit tests — test property sources are installed after
 * environment post-processors run, so they cannot simulate the variable.)
 */
@SpringBootTest
@ActiveProfiles("test")
class OtelEndpointEnvironmentPostProcessorRegistrationTest {

    @Autowired private ConfigurableEnvironment environment;

    @Test
    void postProcessorIsRegisteredAndActive() {
        assertThat(
                        environment
                                .getPropertySources()
                                .contains(OtelEndpointEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .isTrue();
        assertThat(environment.getProperty("management.otlp.metrics.export.enabled"))
                .isEqualTo("false");
        assertThat(environment.getProperty("management.opentelemetry.tracing.export.otlp.endpoint"))
                .isNull();
    }
}
