package com.learnium.generation;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Binds {@link GenerationProperties} and provides the shared clock. */
@Configuration
@EnableConfigurationProperties(GenerationProperties.class)
public class GenerationConfig {

    /**
     * The one clock the generation module reads (job creation day bounds,
     * claim leases, retry backoff), so tests can substitute a mutable
     * clock and move time deterministically instead of sleeping.
     */
    @Bean
    public Clock generationClock() {
        return Clock.systemUTC();
    }
}
