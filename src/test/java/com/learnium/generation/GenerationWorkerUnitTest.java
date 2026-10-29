package com.learnium.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.learnium.model.content.GenerationDifficulty;
import com.learnium.model.content.GenerationFailureKind;
import com.learnium.repository.content.MaterialRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The worker's material-missing guard in isolation: the row cannot be
 * absent through the API (the job FK forbids it), so the branch is
 * proven here with a stubbed repository.
 */
class GenerationWorkerUnitTest {

    @Test
    void missingMaterialRowFailsTheJobWithoutCallingTheGenerator() {
        GenerationClaimService claimService = mock(GenerationClaimService.class);
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        QuestionGenerator generator = mock(QuestionGenerator.class);
        GenerationWorker worker =
                new GenerationWorker(
                        claimService,
                        materialRepository,
                        generator,
                        mock(GenerationPersistenceService.class));
        ClaimedJob claim =
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
        when(claimService.claimNext()).thenReturn(Optional.of(claim));
        when(materialRepository.findById(claim.materialId())).thenReturn(Optional.empty());

        assertThat(worker.runOnce()).isTrue();

        verify(claimService)
                .failTerminal(claim, GenerationFailureKind.MATERIAL_DELETED, null, null, null);
        verifyNoInteractions(generator);
    }
}
