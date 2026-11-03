package com.learnium.exam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nightly purge of proctor snapshots older than the 30-day retention window. */
@Component
public class ProctorRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(ProctorRetentionJob.class);

    private final ProctorService proctor;

    public ProctorRetentionJob(ProctorService proctor) {
        this.proctor = proctor;
    }

    @Scheduled(cron = "${learnium.proctor.retention-cron:0 30 3 * * *}")
    public void purge() {
        int purged = proctor.purgeExpired();
        if (purged > 0) log.info("purged {} expired proctor snapshots", purged);
    }
}
