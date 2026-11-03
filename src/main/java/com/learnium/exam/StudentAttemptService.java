package com.learnium.exam;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.learnium.api.dto.StudentDtos.*;
import com.learnium.common.error.*;
import com.learnium.marking.MarkingJobService;
import com.learnium.model.exam.*;
import com.learnium.repository.exam.*;
import com.learnium.security.LearniumPrincipal;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Own-data student attempt lifecycle over immutable published items. */
@Service
public class StudentAttemptService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ExamInstanceStudentRepository assignments;
    private final ExamInstanceRepository instances;
    private final ExamAttemptRepository attempts;
    private final ExamInstanceQuestionRepository items;
    private final QuestionAttemptRepository answers;
    private final MarkingJobService markingJobs;
    private final Clock clock;
    private final tools.jackson.databind.ObjectMapper webJson;

    public StudentAttemptService(ExamInstanceStudentRepository assignments, ExamInstanceRepository instances,
            ExamAttemptRepository attempts, ExamInstanceQuestionRepository items,
            QuestionAttemptRepository answers, MarkingJobService markingJobs, Clock clock,
            tools.jackson.databind.ObjectMapper webJson) {
        this.assignments = assignments; this.instances = instances; this.attempts = attempts; this.items = items;
        this.answers = answers; this.markingJobs = markingJobs; this.clock = clock; this.webJson = webJson;
    }

    @Transactional
    public AttemptDto start(LearniumPrincipal caller, UUID instanceId) {
        var assignment = assignments.findAssigned(caller.centreId(), instanceId, caller.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ATT_NOT_ASSIGNED));
        ExamInstance instance = instances.lockInCentre(caller.centreId(), instanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATT_NOT_ASSIGNED));
        List<ExamAttempt> owned = attempts.ownedForInstance(caller.centreId(), instanceId, caller.userId());
        var active = owned.stream().filter(row -> row.getStatus() == ExamAttemptStatus.IN_PROGRESS).findFirst();
        if (active.isPresent()) return dto(active.get());
        Instant now = clock.instant();
        if (!available(instance, now) || !"SCORED_TEST".equals(instance.getDefinitionKind()))
            throw new BusinessException(ErrorCode.ATT_NOT_AVAILABLE);
        if (owned.size() >= instance.getMaxAttempts()) throw new BusinessException(ErrorCode.ATT_EXHAUSTED);
        ExamAttempt attempt = new ExamAttempt(); attempt.setCentreId(caller.centreId());
        attempt.setExamInstance(instance); attempt.setUser(assignment.getUser()); attempt.setAttemptNo(owned.size() + 1);
        attempt.setStatus(ExamAttemptStatus.IN_PROGRESS); attempt.setStartedAt(now);
        Instant deadline = instance.getClosesAt();
        if (instance.getTimeLimitSeconds() != null)
            deadline = min(deadline, now.plusSeconds(instance.getTimeLimitSeconds()));
        attempt.setDeadlineAt(deadline);
        attempt.setTotalQuestions(items.frozen(caller.centreId(), instanceId).size());
        return dto(attempts.saveAndFlush(attempt));
    }

    @Transactional(readOnly = true)
    public AttemptDto attempt(LearniumPrincipal caller, UUID attemptId) {
        return dto(owned(caller, attemptId));
    }

    @Transactional
    public SavedAnswerResponse save(LearniumPrincipal caller, UUID attemptId, UUID itemId, tools.jackson.databind.JsonNode supplied) {
        ExamAttempt attempt = locked(caller, attemptId);
        if (attempt.getStatus() != ExamAttemptStatus.IN_PROGRESS || !clock.instant().isBefore(attempt.getDeadlineAt())
                || attempt.getExamInstance().getClosedAt() != null)
            throw new BusinessException(ErrorCode.ATT_NOT_AVAILABLE, "This attempt can no longer be changed");
        ExamInstanceQuestion item = items.frozenItem(caller.centreId(),
                attempt.getExamInstance().getExamInstanceId(), itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATT_NOT_ASSIGNED));
        JsonNode canonical = canonicalAnswer(type(item), internal(supplied));
        QuestionAttempt answer = answers.findSaved(caller.centreId(), attemptId, itemId).orElseGet(() -> {
            QuestionAttempt created = new QuestionAttempt(); created.setCentreId(caller.centreId()); created.setAttempt(attempt);
            created.setExamInstanceQuestion(item); created.setQuestion(item.getQuestion());
            created.setQuestionVersion(item.getQuestionVersion()); created.setPoints(item.getPoints()); return created;
        });
        answer.setAnswerPayload(canonical); answer = answers.saveAndFlush(answer);
        return new SavedAnswerResponse(itemId, web(answer.getAnswerPayload()), answer.getUpdatedAt());
    }

    @Transactional
    public AttemptResultDto submit(LearniumPrincipal caller, UUID attemptId) {
        return finish(locked(caller, attemptId), ExamAttemptStatus.SUBMITTED);
    }

    @Transactional
    public AttemptResultDto autoSubmit(UUID attemptId) {
        ExamAttempt attempt = attempts.lockById(attemptId).orElseThrow();
        return finish(attempt, ExamAttemptStatus.AUTO_SUBMITTED);
    }

    @Transactional(readOnly = true)
    public AttemptResultDto result(LearniumPrincipal caller, UUID attemptId) {
        ExamAttempt attempt = owned(caller, attemptId);
        if (attempt.getStatus() == ExamAttemptStatus.IN_PROGRESS)
            throw new BusinessException(ErrorCode.ATT_NOT_AVAILABLE, "Submit the attempt before viewing results");
        return resultDto(attempt);
    }

    @Transactional(readOnly = true)
    public PracticeDto practice(LearniumPrincipal caller, UUID instanceId) {
        assignments.findAssigned(caller.centreId(), instanceId, caller.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ATT_NOT_ASSIGNED));
        ExamInstance instance = instances.findById(instanceId)
                .filter(row -> row.getCentreId().equals(caller.centreId())).orElseThrow(() -> new BusinessException(ErrorCode.ATT_NOT_ASSIGNED));
        if (!"FLASHCARD_PRACTICE".equals(instance.getDefinitionKind()) || !available(instance, clock.instant()))
            throw new BusinessException(ErrorCode.ATT_NOT_AVAILABLE);
        return new PracticeDto(instanceId, instance.getDefinitionName(), items.frozen(caller.centreId(), instanceId)
                .stream().map(this::practiceItem).toList());
    }

    private AttemptResultDto finish(ExamAttempt attempt, ExamAttemptStatus finalStatus) {
        if (attempt.getStatus() != ExamAttemptStatus.IN_PROGRESS) return resultDto(attempt);
        List<ExamInstanceQuestion> frozen = items.frozen(attempt.getCentreId(), attempt.getExamInstance().getExamInstanceId());
        Map<UUID, QuestionAttempt> saved = answers.findByCentreIdAndAttemptId(attempt.getCentreId(), attempt.getAttemptId())
                .stream().collect(Collectors.toMap(row -> row.getExamInstanceQuestion().getExamInstanceQuestionId(), Function.identity()));
        BigDecimal total = BigDecimal.ZERO; int correct = 0;
        for (ExamInstanceQuestion item : frozen) {
            QuestionAttempt answer = saved.get(item.getExamInstanceQuestionId());
            if (answer == null) {
                answer = blankAnswer(attempt, item); answer = answers.saveAndFlush(answer);
                saved.put(item.getExamInstanceQuestionId(), answer);
            }
            if ("WRITTEN".equals(type(item))) {
                answer.setCorrect(null); answer.setMarkingStatus(MarkingStatus.PENDING); answers.saveAndFlush(answer);
                markingJobs.enqueueMarking(answer.getQuestionAttemptId());
            } else {
                boolean isCorrect = grade(item, answer.getAnswerPayload());
                answer.setCorrect(isCorrect); answer.setMarkingStatus(MarkingStatus.AUTO); answers.save(answer);
                if (isCorrect) { total = total.add(item.getMarks()); correct++; }
            }
        }
        attempt.setStatus(finalStatus); attempt.setSubmittedAt(clock.instant()); attempt.setCorrectCount(correct);
        attempt.setTotalQuestions(frozen.size()); attempt.setTotalScore(total); attempts.saveAndFlush(attempt);
        return resultDto(attempt);
    }

    private AttemptDto dto(ExamAttempt attempt) {
        UUID instanceId = attempt.getExamInstance().getExamInstanceId();
        List<AttemptItemDto> questions = items.frozen(attempt.getCentreId(), instanceId).stream().map(this::studentItem).toList();
        List<SavedAnswerDto> saved = answers.findByCentreIdAndAttemptId(attempt.getCentreId(), attempt.getAttemptId()).stream()
                .map(row -> new SavedAnswerDto(row.getExamInstanceQuestion().getExamInstanceQuestionId(),
                        web(row.getAnswerPayload()), row.getUpdatedAt())).toList();
        long remaining = Math.max(0, Duration.between(clock.instant(), attempt.getDeadlineAt()).toSeconds());
        return new AttemptDto(attempt.getAttemptId(), instanceId, attempt.getExamInstance().getDefinitionName(),
                attempt.getAttemptNo(), attempt.getStatus().name(), attempt.getStartedAt(), attempt.getDeadlineAt(),
                remaining, questions, saved);
    }

    private AttemptResultDto resultDto(ExamAttempt attempt) {
        List<ExamInstanceQuestion> frozen = items.frozen(attempt.getCentreId(), attempt.getExamInstance().getExamInstanceId());
        Map<UUID, QuestionAttempt> saved = answers.findByCentreIdAndAttemptId(attempt.getCentreId(), attempt.getAttemptId())
                .stream().collect(Collectors.toMap(row -> row.getExamInstanceQuestion().getExamInstanceQuestionId(), Function.identity()));
        boolean released = released(attempt.getExamInstance());
        BigDecimal total = BigDecimal.ZERO, maximum = BigDecimal.ZERO; boolean pending = false;
        List<ResultItemDto> rows = new ArrayList<>();
        for (ExamInstanceQuestion item : frozen) {
            maximum = maximum.add(item.getMarks()); QuestionAttempt answer = saved.get(item.getExamInstanceQuestionId());
            String state; BigDecimal score = BigDecimal.ZERO;
            if ("WRITTEN".equals(type(item))) {
                if (answer != null && answer.getMarkingStatus() == MarkingStatus.CONFIRMED) {
                    state = "CONFIRMED"; score = answer.getConfirmedScore() == null ? BigDecimal.ZERO : answer.getConfirmedScore();
                } else { state = "PENDING_MARKING"; pending = true; }
            } else if (answer != null && Boolean.TRUE.equals(answer.getCorrect())) {
                state = "CORRECT"; score = item.getMarks();
            } else state = "INCORRECT";
            total = total.add(score);
            rows.add(new ResultItemDto(item.getExamInstanceQuestionId(), item.getQuestionOrder(), type(item), item.getMarks(),
                    web(safePayload(item)), answer == null ? webJson.readTree("null") : web(answer.getAnswerPayload()), state, score,
                    released ? web(solution(item)) : null,
                    released && answer != null ? answer.getMarkerFeedback() : null));
        }
        return new AttemptResultDto(attempt.getAttemptId(), attempt.getStatus().name(), attempt.getSubmittedAt(),
                total, maximum, pending, released, rows);
    }

    private QuestionAttempt blankAnswer(ExamAttempt attempt, ExamInstanceQuestion item) {
        QuestionAttempt row = new QuestionAttempt(); row.setCentreId(attempt.getCentreId()); row.setAttempt(attempt);
        row.setExamInstanceQuestion(item); row.setQuestion(item.getQuestion()); row.setQuestionVersion(item.getQuestionVersion());
        row.setPoints(item.getPoints()); row.setAnswerPayload("NUMERIC".equals(type(item)) ? JSON.createObjectNode().putNull("number")
                : JSON.createObjectNode().put("WRITTEN".equals(type(item)) || "FILL_BLANK".equals(type(item)) ? "text" : "optionId", ""));
        return row;
    }

    private JsonNode canonicalAnswer(String type, JsonNode supplied) {
        if (supplied == null || supplied.isNull()) throw invalidAnswer();
        ObjectNode value = JSON.createObjectNode();
        if ("MCQ".equals(type)) {
            JsonNode node = supplied.isObject() ? supplied.get("optionId") : supplied;
            if (node == null || !node.isTextual() || node.asText().isBlank()) throw invalidAnswer();
            value.put("optionId", node.asText());
        } else if ("FILL_BLANK".equals(type) || "WRITTEN".equals(type)) {
            JsonNode node = supplied.isObject() ? supplied.get("text") : supplied;
            if (node == null || !node.isTextual() || node.asText().length() > 20000) throw invalidAnswer();
            value.put("text", node.asText());
        } else if ("NUMERIC".equals(type)) {
            JsonNode node = supplied.isObject() ? supplied.get("number") : supplied;
            if (node == null || !node.isNumber()) throw invalidAnswer();
            value.put("number", node.decimalValue());
        } else throw invalidAnswer();
        return value;
    }

    private boolean grade(ExamInstanceQuestion item, JsonNode answer) {
        return switch (type(item)) {
            case "MCQ" -> item.getCorrectRefSnapshot().equals(answer.path("optionId").asText());
            case "FILL_BLANK" -> expectedText(item).trim().equalsIgnoreCase(answer.path("text").asText().trim());
            case "NUMERIC" -> answer.path("number").isNumber() && answer.path("number").decimalValue()
                    .subtract(expectedNumber(item)).abs().compareTo(tolerance(item)) <= 0;
            default -> false;
        };
    }

    private AttemptItemDto studentItem(ExamInstanceQuestion item) {
        return new AttemptItemDto(item.getExamInstanceQuestionId(), item.getQuestionOrder(), type(item), item.getMarks(), web(safePayload(item)));
    }
    private AttemptItemDto practiceItem(ExamInstanceQuestion item) {
        return new AttemptItemDto(item.getExamInstanceQuestionId(), item.getQuestionOrder(), type(item), item.getMarks(), web(item.getPayloadSnapshot()));
    }
    private JsonNode safePayload(ExamInstanceQuestion item) {
        ObjectNode copy = item.getPayloadSnapshot().deepCopy();
        copy.remove(List.of("answer", "answerValue", "tolerance", "workedSolution", "modelAnswer", "rubric", "correctRef", "correctIndex"));
        return copy;
    }
    private JsonNode solution(ExamInstanceQuestion item) {
        ObjectNode out = JSON.createObjectNode(); out.put("correctRef", item.getCorrectRefSnapshot());
        JsonNode payload = item.getPayloadSnapshot();
        for (String key : List.of("answer", "answerValue", "tolerance", "workedSolution", "modelAnswer", "rubric"))
            if (payload.has(key)) out.set(key, payload.get(key).deepCopy());
        return out;
    }
    private String expectedText(ExamInstanceQuestion item) {
        JsonNode value = item.getPayloadSnapshot().get("answer");
        return value != null && value.isTextual() ? value.asText() : item.getCorrectRefSnapshot();
    }
    private BigDecimal expectedNumber(ExamInstanceQuestion item) {
        JsonNode value = item.getPayloadSnapshot().get("answerValue");
        return value != null && value.isNumber() ? value.decimalValue() : new BigDecimal(item.getCorrectRefSnapshot());
    }
    private BigDecimal tolerance(ExamInstanceQuestion item) {
        JsonNode value = item.getPayloadSnapshot().get("tolerance");
        return value != null && value.isNumber() ? value.decimalValue() : BigDecimal.ZERO;
    }
    private String type(ExamInstanceQuestion item) { return item.getQuestion().getQuestionType().getCode(); }
    private boolean released(ExamInstance instance) {
        return "ON_SUBMIT".equals(instance.getAnswerReleasePolicy())
                || ("AFTER_CLOSE".equals(instance.getAnswerReleasePolicy()) && !clock.instant().isBefore(instance.getClosesAt()));
    }
    private boolean available(ExamInstance instance, Instant now) {
        return instance.getClosedAt() == null && !now.isBefore(instance.getOpensAt()) && now.isBefore(instance.getClosesAt());
    }
    private ExamAttempt owned(LearniumPrincipal caller, UUID id) {
        return attempts.findById(id).filter(row -> row.getCentreId().equals(caller.centreId())
                && row.getUser().getUserId().equals(caller.userId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.ATT_NOT_ASSIGNED));
    }
    private ExamAttempt locked(LearniumPrincipal caller, UUID id) {
        return attempts.lockOwned(caller.centreId(), id, caller.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ATT_NOT_ASSIGNED));
    }
    private BusinessException invalidAnswer() { return new BusinessException(ErrorCode.VAL_FAILED, "Answer does not match the question type"); }
    private tools.jackson.databind.JsonNode web(JsonNode value) { return webJson.readTree(value.toString()); }
    private JsonNode internal(tools.jackson.databind.JsonNode value) {
        try { return JSON.readTree(value.toString()); }
        catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw invalidAnswer(); }
    }
    private static Instant min(Instant first, Instant second) { return first.isBefore(second) ? first : second; }
}
