package com.learnium.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Bridges the ops-facing {@code OTEL_EXPORTER_OTLP_ENDPOINT} variable to the
 * Spring Boot 4 export properties — but only when it is actually set.
 *
 * <p>Why not a plain placeholder in application.yml: the OTLP tracing exporter
 * auto-configuration keys off the endpoint property being PRESENT, so binding
 * it unconditionally (to an empty string, or to an unresolved placeholder)
 * makes the exporter start with an invalid endpoint and fail context startup.
 * Mapping it here, only when a real value exists, keeps "unset = no export"
 * true for both signals: with no endpoint, metrics export is also switched
 * off explicitly instead of falling back to Micrometer's default URL.
 */
public class OtelEndpointEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String ENDPOINT_ENV_VAR = "OTEL_EXPORTER_OTLP_ENDPOINT";
    static final String PROPERTY_SOURCE_NAME = "learniumOtlpEndpoint";
    static final String TRACING_ENDPOINT_PROPERTY =
            "management.opentelemetry.tracing.export.otlp.endpoint";
    static final String METRICS_URL_PROPERTY = "management.otlp.metrics.export.url";
    static final String METRICS_ENABLED_PROPERTY = "management.otlp.metrics.export.enabled";

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {
        String endpoint = environment.getProperty(ENDPOINT_ENV_VAR);
        Map<String, Object> properties = new LinkedHashMap<>();
        if (endpoint != null && !endpoint.isBlank()) {
            properties.put(TRACING_ENDPOINT_PROPERTY, endpoint);
            properties.put(METRICS_URL_PROPERTY, endpoint);
        } else {
            properties.put(METRICS_ENABLED_PROPERTY, false);
        }
        environment
                .getPropertySources()
                .addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
    }
}
