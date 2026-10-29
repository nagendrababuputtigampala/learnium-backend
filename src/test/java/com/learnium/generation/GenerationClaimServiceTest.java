package com.learnium.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnium.generation.GenerationOutcome.Success;
import com.learnium.model.content.BackgroundJob;
import com.learnium.model.content.GenerationDifficulty;
import com.learnium.model.content.GenerationFailureKind;
import com.learnium.model.content.Material;
import com.learnium.model.content.Topic;
import com.learnium.model.master.Grade;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.master.Subject;
import com.learnium.model.user.Centre;
import com.learnium.repository.content.BackgroundJobRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The claim protocol's edges that a single-threaded integration test
 * cannot reach deterministically: the lost claim race, ownership-lost
 * completions, and every counts/usage combination of the terminal
 * finisher.
 */
class GenerationClaimServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final BackgroundJobRepository repository = mock(BackgroundJobRepository.class);
    private final GenerationClaimService service =
            new GenerationClaimService(
                    repository, new GenerationProperties(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void noCandidatesMeansNoClaim() {
        when(repository.findClaimCandidateIds(eq(NOW), any())).thenReturn(List.of());

        assertThat(service.claimNext()).isEmpty();
        verify(repository, never()).claim(any(), any(), any(), any());
    }

    @Test
    void losingTheClaimRaceReturnsEmpty() {
        UUID jobId = UUID.randomUUID();
        when(repository.findClaimCandidateIds(eq(NOW), any())).thenReturn(List.of(jobId));
        when(repository.claim(eq(jobId), any(), any(), eq(NOW))).thenReturn(0);

        assertThat(service.claimNext()).isEmpty();
        verify(repository, never()).findClaimSnapshotById(any());
    }

    @Test
    void aWonClaimSnapshotsEveryFrozenInput() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID centreId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        BackgroundJob job = claimedJob(jobId, centreId, creatorId);
        when(repository.findClaimCandidateIds(eq(NOW), any())).thenReturn(List.of(jobId));
        when(repository.claim(eq(jobId), any(), any(), eq(NOW))).thenReturn(1);
        when(repository.findClaimSnapshotById(jobId)).thenReturn(Optional.of(job));

        ClaimedJob claim = service.claimNext().orElseThrow();

        assertThat(claim.jobId()).isEqualTo(jobId);
        assertThat(claim.claimToken()).isNotNull();
        assertThat(claim.claimGeneration()).isEqualTo(1);
        assertThat(claim.attempts()).isEqualTo(1);
        assertThat(claim.centreId()).isEqualTo(centreId);
        assertThat(claim.createdBy()).isEqualTo(creatorId);
        assertThat(claim.topicName()).isEqualTo("Fractions");
        assertThat(claim.gradeName()).isEqualTo("Grade 8");
        assertThat(claim.subjectName()).isEqualTo("Mathematics");
        assertThat(claim.counts()).containsEntry(GeneratedQuestionType.MCQ, 3);
        assertThat(claim.difficulty()).isEqualTo(GenerationDifficulty.HARD);
        // The lease handed to the repository is now + the configured 5 minutes.
        ArgumentCaptor<Instant> lease = ArgumentCaptor.forClass(Instant.class);
        verify(repository).claim(eq(jobId), any(), lease.capture(), eq(NOW));
        assertThat(lease.getValue()).isEqualTo(NOW.plusSeconds(300));
    }

    @Test
    void rescheduleReportsOwnershipAndAppliesBackoffTimesAttempts() {
        ClaimedJob claim = claim(2);
        when(repository.reschedule(
                        eq(claim.jobId()),
                        eq(claim.claimToken()),
                        eq(2),
                        eq(GenerationFailureKind.MODEL_ERROR),
                        eq("Boom"),
                        eq(NOW.plusSeconds(60)),
                        eq(NOW)))
                .thenReturn(1, 0);

        assertThat(service.reschedule(claim, GenerationFailureKind.MODEL_ERROR, "Boom")).isTrue();
        assertThat(service.reschedule(claim, GenerationFailureKind.MODEL_ERROR, "Boom")).isFalse();
    }

    @Test
    void failTerminalPassesCountsAndUsageThroughIndependently() {
        ClaimedJob claim = claim(1);
        GenerationResultCounts counts =
                new GenerationResultCounts(
                        new EnumMap<>(
                                Map.of(
                                        GeneratedQuestionType.MCQ,
                                        new GenerationResultCounts.TypeResult(
                                                1, 1, 0, 1, Map.of()))));
        Success usage = new Success(JSON.createArrayNode(), 10L, 5L, null, null);
        when(repository.completeFailed(
                        any(), any(), anyInt(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        assertThat(
                        service.failTerminal(
                                claim, GenerationFailureKind.ZERO_VALID, null, counts, usage))
                .isTrue();
        verify(repository)
                .completeFailed(
                        eq(claim.jobId()),
                        eq(claim.claimToken()),
                        eq(1),
                        eq(GenerationFailureKind.ZERO_VALID),
                        isNull(),
                        eq(counts.toJson()),
                        eq(10L),
                        eq(5L),
                        isNull(),
                        eq(NOW));

        // Usage with thinking tokens recorded passes them through too.
        Success withThinking = new Success(JSON.createArrayNode(), 10L, 5L, 9L, null);
        assertThat(
                        service.failTerminal(
                                claim, GenerationFailureKind.ZERO_VALID, null, null, withThinking))
                .isTrue();
        verify(repository)
                .completeFailed(
                        eq(claim.jobId()),
                        eq(claim.claimToken()),
                        eq(1),
                        eq(GenerationFailureKind.ZERO_VALID),
                        isNull(),
                        isNull(),
                        eq(10L),
                        eq(5L),
                        eq(9L),
                        eq(NOW));

        assertThat(
                        service.failTerminal(
                                claim, GenerationFailureKind.MATERIAL_DELETED, null, null, null))
                .isTrue();
        verify(repository)
                .completeFailed(
                        eq(claim.jobId()),
                        eq(claim.claimToken()),
                        eq(1),
                        eq(GenerationFailureKind.MATERIAL_DELETED),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(NOW));
    }

    @Test
    void failTerminalReportsLostOwnership() {
        ClaimedJob claim = claim(1);
        when(repository.completeFailed(
                        any(), any(), anyInt(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);

        assertThat(
                        service.failTerminal(
                                claim, GenerationFailureKind.MODEL_ERROR, null, null, null))
                .isFalse();
    }

    private static ClaimedJob claim(int generation) {
        return new ClaimedJob(
                UUID.randomUUID(),
                UUID.randomUUID(),
                generation,
                generation,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "hash",
                UUID.randomUUID(),
                "Fractions",
                "Grade 8",
                "Mathematics",
                Map.of(GeneratedQuestionType.MCQ, 1),
                GenerationDifficulty.MEDIUM,
                "gemini",
                "fake-model",
                "gen-prompt-v1",
                "gen-payload-v1");
    }

    private static BackgroundJob claimedJob(UUID jobId, UUID centreId, UUID creatorId)
            throws Exception {
        Centre centre = new Centre();
        centre.setCentreId(centreId);
        Grade grade = new Grade();
        grade.setName("Grade 8");
        Subject subject = new Subject();
        subject.setSubjectName("Mathematics");
        GradeSubject gradeSubject = new GradeSubject();
        gradeSubject.setGrade(grade);
        gradeSubject.setSubject(subject);
        Material material = new Material();
        material.setGradeSubject(gradeSubject);
        Topic topic = new Topic();
        topic.setTopicName("Fractions");
        BackgroundJob job = new BackgroundJob();
        job.setId(jobId);
        job.setCentre(centre);
        job.setMaterial(material);
        job.setTopic(topic);
        job.setContentHash("hash");
        job.setRequestedCounts(JSON.readTree("{\"MCQ\":3}"));
        job.setDifficulty(GenerationDifficulty.HARD);
        job.setProvider("gemini");
        job.setModel("fake-model");
        job.setPromptVersion("gen-prompt-v1");
        job.setPayloadSchemaVersion("gen-payload-v1");
        job.setClaimGeneration(1);
        job.setAttempts(1);
        job.setCreatedBy(creatorId);
        return job;
    }
}
