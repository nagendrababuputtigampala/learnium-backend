package com.learnium.generation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

/** The scheduler is a thin delegation over the worker's runOnce. */
class GenerationSchedulerTest {

    @Test
    void pollRunsOneWorkerPass() {
        GenerationWorker worker = mock(GenerationWorker.class);

        new GenerationScheduler(worker).poll();

        verify(worker).runOnce();
    }
}
