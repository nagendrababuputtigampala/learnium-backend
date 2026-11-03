package com.learnium.marking;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "learnium.generation.worker-enabled", havingValue = "true", matchIfMissing = true)
public class MarkingScheduler {
    private final MarkingWorker worker;
    public MarkingScheduler(MarkingWorker worker) { this.worker = worker; }
    @Scheduled(fixedDelayString = "${learnium.generation.poll-delay-ms:2000}")
    public void poll() { worker.runOnce(); }
}
