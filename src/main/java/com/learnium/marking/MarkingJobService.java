package com.learnium.marking;

import com.fasterxml.jackson.databind.*;
import com.learnium.common.error.*;
import com.learnium.generation.*;
import com.learnium.model.content.*;
import com.learnium.model.exam.MarkingStatus;
import com.learnium.repository.content.BackgroundJobRepository;
import com.learnium.repository.exam.*;
import com.learnium.repository.user.CentreRepository;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Entry point for M5 submission integration; M4 calls enqueueMarking only from tests. */
@Service
public class MarkingJobService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final BackgroundJobRepository jobs;
    private final QuestionAttemptRepository attempts;
    private final ExamInstanceQuestionRepository frozen;
    private final CentreRepository centres;
    private final GenerationProperties generation;
    private final MarkingProperties properties;
    private final Clock clock;
    public record Claim(UUID id, UUID token, int generation, int attempts, UUID questionAttemptId,
            String provider, String model, String promptVersion, JsonNode input) {}
    public MarkingJobService(BackgroundJobRepository jobs, QuestionAttemptRepository attempts,
            ExamInstanceQuestionRepository frozen, CentreRepository centres, GenerationProperties generation,
            MarkingProperties properties, Clock clock) {
        this.jobs = jobs; this.attempts = attempts; this.frozen = frozen; this.centres = centres;
        this.generation = generation; this.properties = properties; this.clock = clock;
    }

    @Transactional
    public UUID enqueueMarking(UUID questionAttemptId) {
        var answer = attempts.lockForMarking(questionAttemptId).orElseThrow(() -> new BusinessException(ErrorCode.TST_NOT_FOUND));
        var existing = jobs.findByQuestionAttemptId(questionAttemptId);
        if (existing.isPresent()) return existing.get().getId();
        var item = frozen.frozen(answer.getCentreId(), answer.getAttempt().getExamInstance().getExamInstanceId()).stream()
                .filter(f -> f.getQuestion().getQuestionId().equals(answer.getQuestion().getQuestionId()) && f.getQuestionVersion() == answer.getQuestionVersion())
                .findFirst().orElseThrow(() -> invalid("Frozen question is missing"));
        JsonNode payload = item.getPayloadSnapshot();
        if (!"WRITTEN".equals(item.getQuestion().getQuestionType().getCode())
                || new DraftQuestionFactory().validateForReview(GeneratedQuestionType.WRITTEN, payload).draft() == null)
            throw invalid("Marking requires a valid frozen written question");
        Set<String> ids = new HashSet<>();
        for (JsonNode criterion : payload.path("rubric")) {
            if (!criterion.path("id").isTextual() || criterion.path("id").asText().isBlank() || !ids.add(criterion.path("id").asText()))
                throw invalid("Frozen criterion IDs must be present and distinct");
        }
        JsonNode text = answer.getAnswerPayload().path("text");
        if (!text.isTextual() || text.asText().length() > 20000 || answer.getMarkingStatus() == MarkingStatus.CONFIRMED)
            throw invalid("Marking requires an unconfirmed text answer of at most 20000 characters");
        var settings = properties.resolve(generation);
        BackgroundJob job = new BackgroundJob(); job.setJobType("MARKING"); job.setQuestionAttemptId(questionAttemptId);
        job.setCentre(centres.getReferenceById(answer.getCentreId())); job.setCreatedBy(answer.getCreatedBy());
        job.setProvider(settings.provider()); job.setModel(settings.model()); job.setPromptVersion(MarkingService.PROMPT_VERSION);
        job.setPayloadSchemaVersion("marking-v1"); job.setNextAttemptAt(clock.instant());
        var input = JSON.createObjectNode(); input.set("payload", payload.deepCopy()); input.put("answer", text.asText());
        job.setMarkingInput(input); jobs.saveAndFlush(job);
        answer.setMarkingStatus(MarkingStatus.PENDING); attempts.saveAndFlush(answer);
        return job.getId();
    }

    @Transactional
    public Optional<Claim> claimNext() {
        Instant now = clock.instant(); jobs.expireMarkingBudget(now);
        var ids = jobs.markingCandidates(now, PageRequest.of(0, 1));
        if (ids.isEmpty()) return Optional.empty();
        UUID token = UUID.randomUUID(), id = ids.get(0);
        if (jobs.claimMarking(id, token, now.plusSeconds(generation.getLeaseDurationSeconds()), now) == 0) return Optional.empty();
        var job = jobs.findById(id).orElseThrow();
        return Optional.of(new Claim(id, token, job.getClaimGeneration(), job.getAttempts(), job.getQuestionAttemptId(),
                job.getProvider(), job.getModel(), job.getPromptVersion(), job.getMarkingInput().deepCopy()));
    }

    @Transactional
    public boolean succeed(Claim claim, MarkingService.Marked marked) {
        if (jobs.completeSucceeded(claim.id(), claim.token(), claim.generation(), null, null, null, null, clock.instant()) == 0) return false;
        var answer = attempts.lockForMarking(claim.questionAttemptId()).orElseThrow();
        // Confirmation belongs to M5; a late AI result must not overwrite it.
        if (answer.getMarkingStatus() == MarkingStatus.CONFIRMED) return true;
        answer.setAiBreakdown(marked.breakdown().deepCopy()); answer.setAiSuggestedScore(marked.score());
        answer.setMarkerFeedback(marked.breakdown().path("feedback").asText()); answer.setMarkingStatus(MarkingStatus.PENDING);
        attempts.saveAndFlush(answer); return true;
    }

    @Transactional
    public boolean fail(Claim claim, GenerationFailureKind failure, boolean retryable) {
        Instant now = clock.instant();
        if (retryable && claim.attempts() < 2) return jobs.reschedule(claim.id(), claim.token(), claim.generation(), failure, null,
                now.plusSeconds(generation.getRetryBackoffSeconds() * claim.attempts()), now) == 1;
        return jobs.completeFailed(claim.id(), claim.token(), claim.generation(), failure, null, null, null, null, null, now) == 1;
    }

    private BusinessException invalid(String message) { return new BusinessException(ErrorCode.TST_INVALID, message); }
}
