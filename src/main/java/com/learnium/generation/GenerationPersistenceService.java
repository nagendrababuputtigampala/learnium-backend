package com.learnium.generation;

import com.learnium.generation.DraftQuestionFactory.DraftQuestion;
import com.learnium.generation.DraftQuestionFactory.ValidationOutcome;
import com.learnium.generation.GenerationOutcome.Success;
import com.learnium.model.content.GenerationDifficulty;
import com.learnium.model.content.Material;
import com.learnium.model.content.Question;
import com.learnium.model.content.QuestionStatus;
import com.learnium.model.master.QuestionType;
import com.learnium.repository.content.BackgroundJobRepository;
import com.learnium.repository.content.MaterialRepository;
import com.learnium.repository.content.QuestionRepository;
import com.learnium.repository.content.TopicRepository;
import com.learnium.repository.master.QuestionTypeRepository;
import com.learnium.repository.user.CentreRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place drafts are committed. Material re-verification, the
 * ownership-checked job transition and the question inserts happen in
 * ONE transaction, in that order: if the material vanished or changed,
 * or the claim no longer owns the job, nothing is written at all; if a
 * question insert fails, the job transition rolls back with it. That
 * ordering is what makes generation exactly-once per job even when a
 * stale worker finishes late.
 */
@Service
public class GenerationPersistenceService {

    public enum PersistResult {
        COMMITTED,
        LOST_OWNERSHIP,
        MATERIAL_GONE
    }

    private final BackgroundJobRepository jobRepository;
    private final MaterialRepository materialRepository;
    private final QuestionRepository questionRepository;
    private final QuestionTypeRepository questionTypeRepository;
    private final CentreRepository centreRepository;
    private final TopicRepository topicRepository;
    private final Clock clock;

    public GenerationPersistenceService(
            BackgroundJobRepository jobRepository,
            MaterialRepository materialRepository,
            QuestionRepository questionRepository,
            QuestionTypeRepository questionTypeRepository,
            CentreRepository centreRepository,
            TopicRepository topicRepository,
            Clock clock) {
        this.jobRepository = jobRepository;
        this.materialRepository = materialRepository;
        this.questionRepository = questionRepository;
        this.questionTypeRepository = questionTypeRepository;
        this.centreRepository = centreRepository;
        this.topicRepository = topicRepository;
        this.clock = clock;
    }

    @Transactional
    public PersistResult persistSuccess(
            ClaimedJob claim, ValidationOutcome validation, Success success) {
        Material material = materialRepository.findById(claim.materialId()).orElse(null);
        if (material == null
                || material.getDeletedAt() != null
                || !Objects.equals(material.getContentHash(), claim.contentHash())) {
            return PersistResult.MATERIAL_GONE;
        }
        int updated =
                jobRepository.completeSucceeded(
                        claim.jobId(),
                        claim.claimToken(),
                        claim.claimGeneration(),
                        validation.counts().toJson(),
                        success.usageInputTokens(),
                        success.usageOutputTokens(),
                        success.usageThinkingTokens(),
                        clock.instant());
        if (updated == 0) {
            return PersistResult.LOST_OWNERSHIP;
        }
        Map<GeneratedQuestionType, QuestionType> types = loadQuestionTypes(validation);
        List<Question> questions = new ArrayList<>();
        for (DraftQuestion draft : validation.drafts()) {
            questions.add(toQuestion(claim, draft, types.get(draft.type())));
        }
        questionRepository.saveAll(questions);
        questionRepository.flush();
        return PersistResult.COMMITTED;
    }

    private Map<GeneratedQuestionType, QuestionType> loadQuestionTypes(ValidationOutcome validation) {
        Map<GeneratedQuestionType, QuestionType> types = new EnumMap<>(GeneratedQuestionType.class);
        for (DraftQuestion draft : validation.drafts()) {
            types.computeIfAbsent(
                    draft.type(),
                    type ->
                            questionTypeRepository
                                    .findByCode(type.name())
                                    .orElseThrow(
                                            () ->
                                                    new IllegalStateException(
                                                            "Missing question type " + type.name())));
        }
        return types;
    }

    private Question toQuestion(ClaimedJob claim, DraftQuestion draft, QuestionType questionType) {
        Question question = new Question();
        question.setCentre(centreRepository.getReferenceById(claim.centreId()));
        question.setTopic(topicRepository.getReferenceById(claim.topicId()));
        question.setQuestionType(questionType);
        question.setMaterial(materialRepository.getReferenceById(claim.materialId()));
        question.setDifficulty(difficultyValue(claim.difficulty()));
        question.setPayload(draft.payload());
        question.setCorrectRef(draft.correctRef());
        question.setStatus(QuestionStatus.DRAFT);
        question.setGenerationJobId(claim.jobId());
        question.setGenerationProvider(claim.provider());
        question.setGeneratedByModel(claim.model());
        question.setPromptVersion(claim.promptVersion());
        question.setPayloadSchemaVersion(claim.payloadSchemaVersion());
        question.setContentHash(claim.contentHash());
        // The worker has no security context, so the audit creator is the
        // job's creator, set explicitly (as at job creation).
        question.setCreatedBy(claim.createdBy());
        return question;
    }

    /** Job difficulty bands mapped onto the question bank's 1..5 scale. */
    private static short difficultyValue(GenerationDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> 1;
            case MEDIUM -> 3;
            case HARD -> 5;
        };
    }
}
