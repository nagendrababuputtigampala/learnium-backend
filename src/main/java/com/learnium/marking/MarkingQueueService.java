package com.learnium.marking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.learnium.api.dto.MarkingQueueDtos.*;
import com.learnium.common.error.*;
import com.learnium.model.content.*;
import com.learnium.model.exam.*;
import com.learnium.model.user.UserRole;
import com.learnium.repository.content.BackgroundJobRepository;
import com.learnium.repository.exam.QuestionAttemptRepository;
import com.learnium.security.LearniumPrincipal;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff-owned confirmation workflow over frozen written-question facts. */
@Service
public class MarkingQueueService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final QuestionAttemptRepository answers;
    private final BackgroundJobRepository jobs;
    private final MarkingJobService markingJobs;
    private final Clock clock;
    private final tools.jackson.databind.ObjectMapper webJson;

    public MarkingQueueService(QuestionAttemptRepository answers, BackgroundJobRepository jobs,
            MarkingJobService markingJobs, Clock clock, tools.jackson.databind.ObjectMapper webJson) {
        this.answers = answers; this.jobs = jobs; this.markingJobs = markingJobs; this.clock = clock; this.webJson = webJson;
    }

    /** Optional paging over the pending answers (applied before grouping); no params returns the whole queue. */
    @Transactional(readOnly = true)
    public List<QueueGroupDto> queue(LearniumPrincipal caller, Integer page, Integer size) {
        List<QuestionAttempt> rows;
        if (page == null && size == null) {
            rows = answers.markingQueue(caller.centreId(), caller.userId(), admin(caller));
        } else {
            int pageNo = page == null ? 0 : page;
            int pageSize = size == null ? 50 : size;
            if (pageNo < 0 || pageSize < 1 || pageSize > 100)
                throw new BusinessException(ErrorCode.VAL_FAILED, "page must be >= 0 and size between 1 and 100");
            rows = answers.markingQueue(
                    caller.centreId(), caller.userId(), admin(caller),
                    org.springframework.data.domain.PageRequest.of(pageNo, pageSize));
        }
        Map<UUID, BackgroundJob> latest = latest(rows);
        Map<UUID, List<QueueItemDto>> grouped = new LinkedHashMap<>();
        Map<UUID, QuestionAttempt> representatives = new LinkedHashMap<>();
        for (QuestionAttempt row : rows) {
            UUID instanceId = row.getAttempt().getExamInstance().getExamInstanceId();
            representatives.putIfAbsent(instanceId, row);
            grouped.computeIfAbsent(instanceId, ignored -> new ArrayList<>()).add(item(row, state(row, latest.get(row.getQuestionAttemptId()))));
        }
        return grouped.entrySet().stream().map(entry -> {
            QuestionAttempt row = representatives.get(entry.getKey());
            return new QueueGroupDto(entry.getKey(), row.getAttempt().getExamInstance().getDefinitionName(),
                    row.getAttempt().getExamInstance().getBatch().getName(), List.copyOf(entry.getValue()));
        }).toList();
    }

    @Transactional(readOnly = true)
    public MarkingDetailDto detail(LearniumPrincipal caller, UUID id) {
        QuestionAttempt answer = manageable(caller, id);
        BackgroundJob latest = jobs.findLatestByQuestionAttemptId(id, PageRequest.of(0, 1)).stream().findFirst().orElse(null);
        return detail(answer, state(answer, latest));
    }

    @Transactional
    public MarkingDetailDto confirm(LearniumPrincipal caller, UUID id, ConfirmRequest request) {
        QuestionAttempt answer = answers.lockForMarking(id).orElseThrow(() -> new BusinessException(ErrorCode.TST_NOT_FOUND));
        if (answers.manageableWritten(caller.centreId(), caller.userId(), admin(caller), id).isEmpty())
            throw new BusinessException(ErrorCode.TST_NOT_FOUND);
        if (answer.getMarkingStatus() == MarkingStatus.CONFIRMED)
            throw new BusinessException(ErrorCode.MRK_ALREADY_CONFIRMED);
        if (answer.getMarkingStatus() != MarkingStatus.PENDING
                && answer.getMarkingStatus() != MarkingStatus.MARKING_PENDING)
            throw invalid();
        JsonNode rubric = answer.getExamInstanceQuestion().getPayloadSnapshot().path("rubric");
        if (!rubric.isArray() || rubric.isEmpty() || request == null || request.criteria() == null
                || request.feedback() != null && request.feedback().length() > 4000)
            throw invalid();
        Map<String, BigDecimal> limits = new LinkedHashMap<>();
        BigDecimal rubricTotal = BigDecimal.ZERO;
        for (JsonNode criterion : rubric) {
            String criterionId = criterion.path("id").asText(); JsonNode max = criterion.path("maxScore");
            if (criterionId.isBlank() || !max.isNumber() || limits.putIfAbsent(criterionId, max.decimalValue()) != null)
                throw invalid();
            rubricTotal = rubricTotal.add(max.decimalValue());
        }
        Map<String, BigDecimal> submitted = new LinkedHashMap<>();
        for (CriterionScoreRequest criterion : request.criteria()) {
            if (criterion == null || criterion.criterionId() == null || criterion.score() == null
                    || !limits.containsKey(criterion.criterionId()) || submitted.putIfAbsent(criterion.criterionId(), criterion.score()) != null
                    || criterion.score().signum() < 0 || criterion.score().compareTo(limits.get(criterion.criterionId())) > 0)
                throw invalid();
        }
        if (!submitted.keySet().equals(limits.keySet()) || rubricTotal.signum() <= 0) throw invalid();
        BigDecimal criterionTotal = submitted.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal confirmed = criterionTotal.multiply(answer.getExamInstanceQuestion().getMarks())
                .divide(rubricTotal, 2, RoundingMode.HALF_UP);
        ObjectNode breakdown = JSON.createObjectNode(); ObjectNode criteria = breakdown.putObject("criteria");
        submitted.forEach((criterionId, score) -> criteria.putObject(criterionId).put("score", score));
        if (request.feedback() != null) breakdown.put("feedback", request.feedback());
        breakdown.put("rubricTotal", rubricTotal); breakdown.put("criterionTotal", criterionTotal);
        answer.setConfirmedBreakdown(breakdown); answer.setConfirmedScore(confirmed); answer.setMarkerFeedback(request.feedback());
        answer.setMarkedBy(caller.userId()); answer.setMarkedAt(clock.instant()); answer.setMarkingStatus(MarkingStatus.CONFIRMED);
        answers.saveAndFlush(answer);
        return detail(answer, answer.getAiBreakdown() == null ? "NONE" : "READY");
    }

    @Transactional
    public MarkingDetailDto rerun(LearniumPrincipal caller, UUID id) {
        QuestionAttempt answer = manageable(caller, id);
        markingJobs.rerunMarking(id);
        return detail(answer, "MARKING");
    }

    private QuestionAttempt manageable(LearniumPrincipal caller, UUID id) {
        return answers.manageableWritten(caller.centreId(), caller.userId(), admin(caller), id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TST_NOT_FOUND));
    }
    private Map<UUID, BackgroundJob> latest(List<QuestionAttempt> rows) {
        if (rows.isEmpty()) return Map.of();
        Set<UUID> ids = rows.stream().map(QuestionAttempt::getQuestionAttemptId).collect(Collectors.toSet());
        return jobs.markingHistory(List.copyOf(ids)).stream().collect(Collectors.toMap(BackgroundJob::getQuestionAttemptId,
                Function.identity(), (first, ignored) -> first, LinkedHashMap::new));
    }
    private QueueItemDto item(QuestionAttempt row, String state) {
        return new QueueItemDto(row.getQuestionAttemptId(), row.getAttempt().getUser().getUserId(),
                row.getAttempt().getUser().getDisplayName(), row.getAttempt().getExamInstance().getDefinitionName(),
                row.getAttempt().getSubmittedAt(), row.getExamInstanceQuestion().getMarks(), state);
    }
    private MarkingDetailDto detail(QuestionAttempt answer, String state) {
        ExamInstance instance = answer.getAttempt().getExamInstance();
        return new MarkingDetailDto(answer.getQuestionAttemptId(), instance.getExamInstanceId(), instance.getDefinitionName(),
                instance.getBatch().getName(), answer.getAttempt().getUser().getUserId(), answer.getAttempt().getUser().getDisplayName(),
                answer.getAttempt().getSubmittedAt(), answer.getExamInstanceQuestion().getMarks(), web(answer.getExamInstanceQuestion().getPayloadSnapshot()),
                web(answer.getAnswerPayload()), nullable(answer.getAiBreakdown()), answer.getAiBreakdown() == null ? null : answer.getAiBreakdown().path("feedback").asText(null),
                state, nullable(answer.getConfirmedBreakdown()), answer.getConfirmedScore(), answer.getMarkerFeedback());
    }
    private String state(QuestionAttempt answer, BackgroundJob job) {
        if (answer.getAiBreakdown() != null) return "READY";
        if (job == null) return "NONE";
        if (job.getStatus() == BackgroundJobStatus.PENDING || job.getStatus() == BackgroundJobStatus.RUNNING) return "MARKING";
        return job.getStatus() == BackgroundJobStatus.FAILED ? "FAILED" : "NONE";
    }
    private boolean admin(LearniumPrincipal caller) { return caller.role() == UserRole.CENTRE_ADMIN; }
    private BusinessException invalid() { return new BusinessException(ErrorCode.MRK_INVALID); }
    private tools.jackson.databind.JsonNode nullable(JsonNode value) { return value == null ? null : web(value); }
    private tools.jackson.databind.JsonNode web(JsonNode value) { return webJson.readTree(value.toString()); }
}
