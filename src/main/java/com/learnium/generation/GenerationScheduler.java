package com.learnium.generation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The worker's clock: polls for due jobs and runs one per pass. Present
 * only when the worker is enabled — tests disable it and drive
 * {@link GenerationWorker#runOnce()} by hand.
 */
@Component
@ConditionalOnProperty(
        name = "learnium.generation.worker-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class GenerationScheduler {

    private final GenerationWorker worker;

    public GenerationScheduler(GenerationWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${learnium.generation.poll-delay-ms:2000}")
    public void poll() {
        worker.runOnce();
    }
}
