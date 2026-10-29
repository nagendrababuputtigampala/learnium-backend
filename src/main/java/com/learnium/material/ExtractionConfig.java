package com.learnium.material;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@link ExtractionProperties}. */
@Configuration
@EnableConfigurationProperties(ExtractionProperties.class)
public class ExtractionConfig {}
