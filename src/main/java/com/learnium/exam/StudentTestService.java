package com.learnium.exam;

import com.learnium.api.dto.StudentDtos.StudentTestDto;
import com.learnium.model.exam.ExamAttemptStatus;
import com.learnium.repository.exam.ExamInstanceStudentRepository;
import com.learnium.security.LearniumPrincipal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentTestService {
    private final ExamInstanceStudentRepository assignments;
    private final Clock clock;
    public StudentTestService(ExamInstanceStudentRepository assignments, Clock clock) {
        this.assignments = assignments; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<StudentTestDto> tests(LearniumPrincipal caller) {
        Map<UUID, List<ExamInstanceStudentRepository.StudentTestRow>> grouped = new LinkedHashMap<>();
        for (var row : assignments.studentTests(caller.centreId(), caller.userId()))
            grouped.computeIfAbsent(row.getInstanceId(), ignored -> new ArrayList<>()).add(row);
        Instant now = clock.instant();
        return grouped.values().stream().map(rows -> dto(rows, now)).toList();
    }

    private StudentTestDto dto(List<ExamInstanceStudentRepository.StudentTestRow> rows, Instant now) {
        var first = rows.get(0);
        var attempts = rows.stream().filter(row -> row.getAttemptNo() != null).toList();
        var latest = attempts.stream().max(Comparator.comparingInt(row -> row.getAttemptNo())).orElse(null);
        boolean closed = first.getClosedAt() != null || !now.isBefore(first.getClosesAt());
        String state;
        if (now.isBefore(first.getOpensAt())) state = "UPCOMING";
        else if (closed) state = "COMPLETED";
        else if (latest != null && latest.getAttemptStatus() == ExamAttemptStatus.IN_PROGRESS) state = "AVAILABLE";
        else state = attempts.size() < first.getMaxAttempts() ? "AVAILABLE" : "COMPLETED";
        boolean submitted = latest != null && (latest.getAttemptStatus() == ExamAttemptStatus.SUBMITTED
                || latest.getAttemptStatus() == ExamAttemptStatus.AUTO_SUBMITTED);
        return new StudentTestDto(first.getInstanceId(), first.getDefinitionName(), first.getDefinitionKind(),
                first.getBatchName(), first.getOpensAt(), first.getClosesAt(), first.getMaxAttempts(), attempts.size(),
                latest == null ? null : latest.getAttemptId(), latest == null ? null : latest.getAttemptStatus().name(), state,
                submitted ? latest.getTotalScore() : null, submitted && latest.getMarkingPending());
    }
}
