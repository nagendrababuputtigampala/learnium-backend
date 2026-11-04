package com.learnium.exam;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class ProctorRetentionJobTest {
    @Test
    void purgeDelegatesToTheProctorService() {
        ProctorService proctor = mock(ProctorService.class);
        ProctorRetentionJob job = new ProctorRetentionJob(proctor);
        when(proctor.purgeExpired()).thenReturn(3);
        job.purge();
        when(proctor.purgeExpired()).thenReturn(0);
        job.purge();
        verify(proctor, org.mockito.Mockito.times(2)).purgeExpired();
    }
}
