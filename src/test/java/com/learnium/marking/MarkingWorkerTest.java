package com.learnium.marking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnium.generation.GenerationProperties;
import com.learnium.model.content.GenerationFailureKind;
import com.learnium.repository.content.BackgroundJobRepository;
import com.learnium.repository.exam.*;
import com.learnium.repository.user.CentreRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MarkingWorkerTest {
    @Test void isolatesUnexpectedAdapterFailuresAndPersistenceRollbacks() {
        var jobs = mock(MarkingJobService.class); var marking = mock(MarkingService.class);
        var claim = new MarkingJobService.Claim(UUID.randomUUID(), UUID.randomUUID(), 1, 1, UUID.randomUUID(), "ollama", "m", MarkingService.PROMPT_VERSION, new ObjectMapper().createObjectNode());
        when(jobs.claimNext()).thenReturn(Optional.empty(), Optional.of(claim), Optional.of(claim));
        var worker = new MarkingWorker(jobs, marking);
        assertThat(worker.runOnce()).isFalse();
        when(marking.mark(anyString(), anyString(), anyString(), any())).thenThrow(new IllegalStateException("sensitive text must never be logged"));
        assertThat(worker.runOnce()).isTrue(); verify(jobs).fail(claim, GenerationFailureKind.MODEL_ERROR, true);
        doReturn(new MarkingService.Outcome(new MarkingService.Marked(java.math.BigDecimal.ONE, new ObjectMapper().createObjectNode()), null, false)).when(marking).mark(anyString(), anyString(), anyString(), any());
        doThrow(new IllegalStateException("database unavailable")).when(jobs).succeed(any(), any());
        assertThat(worker.runOnce()).isTrue();
        new MarkingScheduler(worker).poll();
    }
    @Test void aLostAtomicClaimDoesNotRunAModelOrReadItsInput() {
        var repository = mock(BackgroundJobRepository.class); UUID id = UUID.randomUUID();
        when(repository.markingCandidates(any(), any())).thenReturn(List.of(id));
        var service = new MarkingJobService(repository, mock(QuestionAttemptRepository.class), mock(ExamInstanceQuestionRepository.class),
                mock(CentreRepository.class), new GenerationProperties(), new MarkingProperties(), Clock.fixed(Instant.now(), ZoneOffset.UTC));
        assertThat(service.claimNext()).isEmpty(); verify(repository, never()).findById(any());
    }
}
