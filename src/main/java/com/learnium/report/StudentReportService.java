package com.learnium.report;

import com.learnium.api.dto.ReportDtos.GenerateReportRequest;
import com.learnium.api.dto.ReportDtos.ReportOverallDto;
import com.learnium.api.dto.ReportDtos.ReportTestDto;
import com.learnium.api.dto.ReportDtos.ReportTopicDto;
import com.learnium.api.dto.ReportDtos.StoredReportFacts;
import com.learnium.api.dto.ReportDtos.StudentReportDto;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.exam.StudentReport;
import com.learnium.model.user.Batch;
import com.learnium.model.user.MemberRole;
import com.learnium.model.user.MembershipStatus;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.exam.StudentReportRepository;
import com.learnium.repository.user.BatchMembershipRepository;
import com.learnium.repository.user.BatchRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.security.LearniumPrincipal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Student report snapshots (M6 task 30). Generating freezes the current facts
 * into a STUDENT_REPORT row; reads return the stored snapshot verbatim, so a
 * later marking confirmation never silently changes a report a parent may
 * already have received — staff regenerate explicitly. Scope rules mirror the
 * batch report: centre admin sees the centre, a teacher only students they
 * teach; everything else reads as not found.
 */
@Service
public class StudentReportService {

    private final UserAccountRepository users;
    private final BatchRepository batches;
    private final BatchMembershipRepository memberships;
    private final ReportFactsService facts;
    private final StudentReportRepository reports;
    private final ReportFactsJson factsJson;

    public StudentReportService(
            UserAccountRepository users,
            BatchRepository batches,
            BatchMembershipRepository memberships,
            ReportFactsService facts,
            StudentReportRepository reports,
            ReportFactsJson factsJson) {
        this.users = users;
        this.batches = batches;
        this.memberships = memberships;
        this.facts = facts;
        this.reports = reports;
        this.factsJson = factsJson;
    }

    @Transactional
    public StudentReportDto generate(LearniumPrincipal caller, UUID studentId, GenerateReportRequest request) {
        UserAccount student = requireStudent(caller, studentId);
        Batch batch = resolveScope(caller, studentId, request.batchId());
        var current = facts.studentFacts(caller.centreId(), studentId, request.batchId());
        StoredReportFacts stored = new StoredReportFacts(
                student.getDisplayName(),
                student.getUsername(),
                batch == null ? null : batch.getName(),
                new ReportOverallDto(
                        current.overall().confirmedItems(),
                        current.overall().pendingItems(),
                        current.overall().earnedMarks(),
                        current.overall().availableMarks(),
                        current.overall().percentage(),
                        current.overall().markingCoveragePercent()),
                current.topics().stream()
                        .map(topic -> new ReportTopicDto(
                                topic.topicId(),
                                topic.topicName(),
                                topic.confirmedItems(),
                                topic.pendingItems(),
                                topic.earnedMarks(),
                                topic.availableMarks(),
                                topic.percentage(),
                                topic.band()))
                        .toList(),
                current.tests().stream()
                        .map(test -> new ReportTestDto(
                                test.attemptId(),
                                test.testName(),
                                test.batchName(),
                                test.submittedAt(),
                                test.earnedMarks(),
                                test.availableMarks(),
                                test.percentage(),
                                test.pendingItems()))
                        .toList());
        StudentReport report = new StudentReport();
        report.setCentreId(caller.centreId());
        report.setUserId(studentId);
        report.setBatchId(request.batchId());
        report.setFacts(factsJson.write(stored));
        report.setTeacherComment(request.teacherComment());
        report.setGeneratedBy(caller.userId());
        report.setGeneratedAt(Instant.now());
        reports.save(report);
        return toDto(report, stored);
    }

    /** Latest snapshot in the requested scope (batchId null = the all-batches scope). */
    @Transactional(readOnly = true)
    public StudentReportDto latest(LearniumPrincipal caller, UUID studentId, UUID batchId) {
        requireStudent(caller, studentId);
        resolveScope(caller, studentId, batchId);
        StudentReport report = findLatest(caller.centreId(), studentId, batchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SYS_NOT_FOUND));
        return toDto(report, factsJson.read(report.getFacts()));
    }

    /** Latest snapshot in any scope, for the student's own reads (PDF download). */
    @Transactional(readOnly = true)
    public StudentReportDto latestForStudent(LearniumPrincipal student) {
        StudentReport report = reports
                .findFirstByCentreIdAndUserIdOrderByGeneratedAtDescReportIdDesc(student.centreId(), student.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SYS_NOT_FOUND));
        return toDto(report, factsJson.read(report.getFacts()));
    }

    private java.util.Optional<StudentReport> findLatest(UUID centreId, UUID studentId, UUID batchId) {
        return batchId == null
                ? reports.findFirstByCentreIdAndUserIdAndBatchIdIsNullOrderByGeneratedAtDescReportIdDesc(
                        centreId, studentId)
                : reports.findFirstByCentreIdAndUserIdAndBatchIdOrderByGeneratedAtDescReportIdDesc(
                        centreId, studentId, batchId);
    }

    private UserAccount requireStudent(LearniumPrincipal caller, UUID studentId) {
        return users.findByCentreIdAndUserId(caller.centreId(), studentId)
                .filter(user -> user.getRole() == UserRole.STUDENT)
                .orElseThrow(() -> new BusinessException(ErrorCode.USR_NOT_FOUND));
    }

    /**
     * Validates the optional batch scope and the caller's right to report on
     * this student; returns the batch when a scope was given.
     */
    private Batch resolveScope(LearniumPrincipal caller, UUID studentId, UUID batchId) {
        if (batchId == null) {
            if (caller.role() == UserRole.TEACHER
                    && !memberships.teacherManagesStudent(caller.centreId(), caller.userId(), studentId)) {
                throw new BusinessException(ErrorCode.TST_NOT_FOUND);
            }
            return null;
        }
        Batch batch = batches.findByCentreIdAndBatchId(caller.centreId(), batchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TST_NOT_FOUND));
        if (caller.role() == UserRole.TEACHER
                && !memberships.existsByCentreIdAndBatchIdAndUserIdAndMemberRoleAndStatus(
                        caller.centreId(),
                        batchId,
                        caller.userId(),
                        MemberRole.TEACHER,
                        MembershipStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.TST_NOT_FOUND);
        }
        if (!memberships.existsByCentreIdAndBatchIdAndUserIdAndMemberRoleAndStatus(
                caller.centreId(), batchId, studentId, MemberRole.STUDENT, MembershipStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.TST_NOT_FOUND);
        }
        return batch;
    }

    private StudentReportDto toDto(StudentReport report, StoredReportFacts stored) {
        return new StudentReportDto(
                report.getReportId(),
                report.getUserId(),
                report.getBatchId(),
                report.getTeacherComment(),
                report.getGeneratedBy(),
                report.getGeneratedAt(),
                stored);
    }


}
