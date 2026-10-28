package com.learnium.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class OtelEndpointEnvironmentPostProcessorTest {

    private final OtelEndpointEnvironmentPostProcessor processor =
            new OtelEndpointEnvironmentPostProcessor();

    @Test
    void endpointSetMapsBothBootExportProperties() {
        MockEnvironment environment =
                new MockEnvironment()
                        .withProperty(
                                OtelEndpointEnvironmentPostProcessor.ENDPOINT_ENV_VAR,
                                "https://otlp.example.com:4318");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("management.opentelemetry.tracing.export.otlp.endpoint"))
                .isEqualTo("https://otlp.example.com:4318");
        assertThat(environment.getProperty("management.otlp.metrics.export.url"))
                .isEqualTo("https://otlp.example.com:4318");
        MapPropertySource source =
                (MapPropertySource)
                        environment
                                .getPropertySources()
                                .get(OtelEndpointEnvironmentPostProcessor.PROPERTY_SOURCE_NAME);
        assertThat(source).isNotNull();
        assertThat(source.getSource())
                .doesNotContainKey("management.otlp.metrics.export.enabled");
    }

    @Test
    void endpointUnsetDisablesMetricsExportAndMapsNoEndpoint() {
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("management.otlp.metrics.export.enabled"))
                .isEqualTo("false");
        assertThat(environment.getProperty("management.opentelemetry.tracing.export.otlp.endpoint"))
                .isNull();
        assertThat(environment.getProperty("management.otlp.metrics.export.url")).isNull();
    }

    @Test
    void blankEndpointIsTreatedAsUnset() {
        MockEnvironment environment =
                new MockEnvironment()
                        .withProperty(OtelEndpointEnvironmentPostProcessor.ENDPOINT_ENV_VAR, "  ");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("management.otlp.metrics.export.enabled"))
                .isEqualTo("false");
        assertThat(environment.getProperty("management.opentelemetry.tracing.export.otlp.endpoint"))
                .isNull();
    }
}
