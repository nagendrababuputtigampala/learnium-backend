package com.learnium.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnium.generation.DraftQuestionFactory.ValidationOutcome;
import com.learnium.generation.GenerationOutcome.Success;
import com.learnium.generation.GenerationPersistenceService.PersistResult;
import com.learnium.model.content.GenerationDifficulty;
import com.learnium.model.content.Material;
import com.learnium.repository.content.BackgroundJobRepository;
import com.learnium.repository.content.MaterialRepository;
import com.learnium.repository.content.QuestionRepository;
import com.learnium.repository.content.TopicRepository;
import com.learnium.repository.master.QuestionTypeRepository;
import com.learnium.repository.user.CentreRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The persist transaction's two non-committal outcomes, isolated: a
 * material row that no longer exists at all (unreachable through the
 * API thanks to the FK, but the guard is the contract) and a lost
 * ownership race.
 */
class GenerationPersistenceServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final BackgroundJobRepository jobRepository = mock(BackgroundJobRepository.class);
    private final MaterialRepository materialRepository = mock(MaterialRepository.class);
    private final QuestionRepository questionRepository = mock(QuestionRepository.class);
    private final GenerationPersistenceService service =
            new GenerationPersistenceService(
                    jobRepository,
                    materialRepository,
                    questionRepository,
                    mock(QuestionTypeRepository.class),
                    mock(CentreRepository.class),
                    mock(TopicRepository.class),
                    Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC));

    private final ClaimedJob claim =
            new ClaimedJob(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    1,
                    1,
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
    private final ValidationOutcome validation =
            new ValidationOutcome(
                    new GenerationResultCounts(new EnumMap<>(GeneratedQuestionType.class)),
                    List.of());
    private final Success success = new Success(JSON.createArrayNode(), 1L, 2L, null, null);

    @Test
    void missingMaterialRowIsMaterialGoneAndWritesNothing() {
        when(materialRepository.findById(claim.materialId())).thenReturn(Optional.empty());

        assertThat(service.persistSuccess(claim, validation, success))
                .isEqualTo(PersistResult.MATERIAL_GONE);
        verify(jobRepository, never())
                .completeSucceeded(any(), any(), anyInt(), any(), any(), any(), any(), any());
        verify(questionRepository, never()).saveAll(any());
    }

    @Test
    void lostOwnershipWritesNoQuestions() {
        Material material = new Material();
        material.setContentHash("hash");
        when(materialRepository.findById(claim.materialId())).thenReturn(Optional.of(material));
        when(jobRepository.completeSucceeded(
                        eq(claim.jobId()), eq(claim.claimToken()), eq(1), any(), any(), any(), any(), any()))
                .thenReturn(0);

        assertThat(service.persistSuccess(claim, validation, success))
                .isEqualTo(PersistResult.LOST_OWNERSHIP);
        verify(questionRepository, never()).saveAll(any());
    }
}
