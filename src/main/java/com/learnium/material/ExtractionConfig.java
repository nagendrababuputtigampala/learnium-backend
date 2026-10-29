package com.learnium.material;

import java.util.concurrent.ExecutorService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Binds {@link ExtractionProperties} and provides the parser pool. */
@Configuration
@EnableConfigurationProperties(ExtractionProperties.class)
public class ExtractionConfig {

    /**
     * Dedicated pool for PDF parsing. Threads are daemons and the pool is
     * cached, so a parse that ignores interruption can be abandoned at its
     * time budget without stalling the request thread or JVM shutdown, and
     * later extractions are not starved by the zombie.
     */
    @Bean(name = "extractionExecutor", destroyMethod = "shutdownNow")
    ExecutorService extractionExecutor() {
        return ExtractionService.defaultExecutor();
    }
}
