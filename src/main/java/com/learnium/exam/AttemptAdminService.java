package com.learnium.exam;

import com.learnium.admin.BatchAccessGuard;
import com.learnium.api.dto.AttemptAdminDtos.InstanceAttemptDto;
import com.learnium.api.dto.IntegrityDtos.IntegrityEventDto;
import com.learnium.api.dto.ProctorDtos.SnapshotDto;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.exam.ExamAttempt;
import com.learnium.model.exam.ExamAttemptStatus;
import com.learnium.model.exam.ExamInstance;
import com.learnium.model.exam.MarkingStatus;
import com.learnium.model.exam.ProctorSnapshot;
import com.learnium.model.exam.QuestionAttempt;
import com.learnium.model.user.UserRole;
import com.learnium.repository.exam.ExamAttemptRepository;
import com.learnium.repository.exam.ExamInstanceQuestionRepository;
import com.learnium.repository.exam.ExamInstanceRepository;
import com.learnium.repository.exam.IntegrityEventRepository;
import com.learnium.repository.exam.ProctorSnapshotRepository;
import com.learnium.repository.exam.QuestionAttemptRepository;
import com.learnium.security.LearniumPrincipal;
import com.learnium.storage.StorageService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teacher-side attempt oversight for one published instance: the attempt list
 * with live score state and integrity flag counts, one attempt's flag
 * timeline, and invalidation. Access follows instance management — centre
 * admins, plus teachers with an ACTIVE membership on the instance's batch;
 * everyone else sees a plain not-found, as in the marking queue.
 */
@Service
public class AttemptAdminService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AttemptAdminService.class);

    private final ExamInstanceRepository instances;
    private final ExamAttemptRepository attempts;
    private final QuestionAttemptRepository answers;
    private final ExamInstanceQuestionRepository items;
    private final IntegrityEventRepository events;
    private final ProctorSnapshotRepository snapshots;
    private final StorageService storage;
    private final BatchAccessGuard batchAccess;

    public AttemptAdminService(
            ExamInstanceRepository instances,
            ExamAttemptRepository attempts,
            QuestionAttemptRepository answers,
            ExamInstanceQuestionRepository items,
            IntegrityEventRepository events,
            ProctorSnapshotRepository snapshots,
            StorageService storage,
            BatchAccessGuard batchAccess) {
        this.instances = instances;
        this.attempts = attempts;
        this.answers = answers;
        this.items = items;
        this.events = events;
        this.snapshots = snapshots;
        this.storage = storage;
        this.batchAccess = batchAccess;
    }

    @Transactional(readOnly = true)
    public List<InstanceAttemptDto> attempts(LearniumPrincipal caller, UUID instanceId) {
        ExamInstance instance = manageableInstance(caller, instanceId);
        List<ExamAttempt> rows =
                attempts.findByCentreIdAndExamInstanceId(caller.centreId(), instanceId).stream()
                        .sorted(
                                java.util.Comparator.comparing(ExamAttempt::getStartedAt)
                                        .thenComparing(row -> row.getAttemptId().toString()))
                        .toList();
        Map<UUID, List<QuestionAttempt>> byAttempt =
                answers.findByCentreIdAndInstanceId(caller.centreId(), instanceId).stream()
                        .collect(Collectors.groupingBy(row -> row.getAttempt().getAttemptId()));
        Map<UUID, Map<String, Long>> flags = flagCounts(caller.centreId(), rows);
        BigDecimal maxScore =
                items.frozen(caller.centreId(), instanceId).stream()
                        .map(item -> item.getMarks())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        return rows.stream()
                .map(row -> dto(row, byAttempt.getOrDefault(row.getAttemptId(), List.of()), maxScore,
                        flags.getOrDefault(row.getAttemptId(), Map.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IntegrityEventDto> timeline(LearniumPrincipal caller, UUID attemptId) {
        manageableAttempt(caller, attemptId);
        return events.findByCentreIdAndAttemptAttemptIdOrderByOccurredAtAsc(caller.centreId(), attemptId).stream()
                .map(event -> new IntegrityEventDto(
                        event.getEventId(), event.getEventType().name(), event.getOccurredAt()))
                .toList();
    }

    /** Invalidating is idempotent; an invalidated attempt drops out of results, dashboards and the marking queue. */
    @Transactional
    public InstanceAttemptDto invalidate(LearniumPrincipal caller, UUID attemptId) {
        ExamAttempt attempt =
                attempts.lockManageable(caller.centreId(), caller.userId(), admin(caller), attemptId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.TST_NOT_FOUND));
        if (attempt.getStatus() != ExamAttemptStatus.INVALIDATED) {
            attempt.setStatus(ExamAttemptStatus.INVALIDATED);
            attempts.save(attempt);
        }
        UUID instanceId = attempt.getExamInstance().getExamInstanceId();
        BigDecimal maxScore =
                items.frozen(caller.centreId(), instanceId).stream()
                        .map(item -> item.getMarks())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        return dto(
                attempt,
                answers.findByCentreIdAndAttemptId(caller.centreId(), attemptId),
                maxScore,
                flagCounts(caller.centreId(), List.of(attempt)).getOrDefault(attemptId, Map.of()));
    }

    @Transactional(readOnly = true)
    public List<SnapshotDto> snapshots(LearniumPrincipal caller, UUID attemptId) {
        manageableAttempt(caller, attemptId);
        return snapshots.findByCentreIdAndAttemptAttemptIdOrderByCapturedAtAsc(caller.centreId(), attemptId).stream()
                .map(snapshot -> new SnapshotDto(
                        snapshot.getSnapshotId(), snapshot.getReason().name(), snapshot.getCapturedAt()))
                .toList();
    }

    /**
     * Streams one snapshot image to managing staff. EVERY view is audit-logged
     * (viewer, attempt, snapshot ids — never image bytes or content).
     */
    @Transactional(readOnly = true)
    public SnapshotImage snapshotImage(LearniumPrincipal caller, UUID attemptId, UUID snapshotId) {
        manageableAttempt(caller, attemptId);
        ProctorSnapshot snapshot =
                snapshots
                        .findByCentreIdAndSnapshotId(caller.centreId(), snapshotId)
                        .filter(row -> row.getAttempt() != null && row.getAttempt().getAttemptId().equals(attemptId))
                        .orElseThrow(() -> new BusinessException(ErrorCode.TST_NOT_FOUND));
        log.info(
                "proctor snapshot viewed viewerId={} attemptId={} snapshotId={}",
                caller.userId(),
                attemptId,
                snapshotId);
        return new SnapshotImage(storage.read(snapshot.getStorageKey()), contentType(snapshot.getStorageKey()));
    }

    public record SnapshotImage(byte[] content, String contentType) {}

    private String contentType(String storageKey) {
        if (storageKey.endsWith(".png")) return "image/png";
        if (storageKey.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private Map<UUID, Map<String, Long>> flagCounts(UUID centreId, List<ExamAttempt> rows) {
        if (rows.isEmpty()) return Map.of();
        Map<UUID, Map<String, Long>> result = new LinkedHashMap<>();
        for (var flag : events.flagCounts(centreId, rows.stream().map(ExamAttempt::getAttemptId).toList())) {
            result.computeIfAbsent(flag.getAttemptId(), key -> new LinkedHashMap<>())
                    .put(flag.getEventType().name(), flag.getEventCount());
        }
        return result;
    }

    private InstanceAttemptDto dto(
            ExamAttempt attempt, List<QuestionAttempt> attemptAnswers, BigDecimal maxScore, Map<String, Long> flags) {
        boolean submitted =
                attempt.getStatus() == ExamAttemptStatus.SUBMITTED
                        || attempt.getStatus() == ExamAttemptStatus.AUTO_SUBMITTED;
        BigDecimal score = null;
        boolean markingPending = false;
        if (submitted) {
            score = BigDecimal.ZERO;
            for (QuestionAttempt answer : attemptAnswers) {
                if (answer.getMarkingStatus() == MarkingStatus.AUTO && Boolean.TRUE.equals(answer.getCorrect())) {
                    score = score.add(answer.getExamInstanceQuestion().getMarks());
                } else if (answer.getMarkingStatus() == MarkingStatus.CONFIRMED) {
                    score = score.add(
                            answer.getConfirmedScore() == null ? BigDecimal.ZERO : answer.getConfirmedScore());
                }
                if (answer.getMarkingStatus() == MarkingStatus.PENDING
                        || answer.getMarkingStatus() == MarkingStatus.MARKING_PENDING) {
                    markingPending = true;
                }
            }
        }
        return new InstanceAttemptDto(
                attempt.getAttemptId(),
                attempt.getUser().getUserId(),
                attempt.getUser().getDisplayName(),
                attempt.getUser().getUsername(),
                attempt.getAttemptNo(),
                attempt.getStatus().name(),
                attempt.getStartedAt(),
                attempt.getSubmittedAt(),
                score,
                maxScore,
                markingPending,
                flags,
                flags.values().stream().mapToLong(Long::longValue).sum());
    }

    private ExamInstance manageableInstance(LearniumPrincipal caller, UUID instanceId) {
        ExamInstance instance =
                instances
                        .findById(instanceId)
                        .filter(row -> row.getCentreId().equals(caller.centreId()))
                        .orElseThrow(() -> new BusinessException(ErrorCode.TST_NOT_FOUND));
        try {
            batchAccess.requireBatchAccess(caller, instance.getBatch().getBatchId());
        } catch (BusinessException e) {
            throw new BusinessException(ErrorCode.TST_NOT_FOUND);
        }
        return instance;
    }

    private ExamAttempt manageableAttempt(LearniumPrincipal caller, UUID attemptId) {
        return attempts.manageable(caller.centreId(), caller.userId(), admin(caller), attemptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TST_NOT_FOUND));
    }

    private boolean admin(LearniumPrincipal caller) {
        return caller.role() == UserRole.CENTRE_ADMIN;
    }
}
