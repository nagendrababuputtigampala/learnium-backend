package com.learnium.repository.exam;

import com.learnium.model.exam.StudentReport;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentReportRepository extends JpaRepository<StudentReport, UUID> {

    /** Latest snapshot for a student within one batch scope. */
    Optional<StudentReport> findFirstByCentreIdAndUserIdAndBatchIdOrderByGeneratedAtDescReportIdDesc(
            UUID centreId, UUID userId, UUID batchId);

    /** Latest snapshot for a student across all batches (null-batch scope). */
    Optional<StudentReport> findFirstByCentreIdAndUserIdAndBatchIdIsNullOrderByGeneratedAtDescReportIdDesc(
            UUID centreId, UUID userId);

    /** Latest snapshot for a student in any scope (student-side reads). */
    Optional<StudentReport> findFirstByCentreIdAndUserIdOrderByGeneratedAtDescReportIdDesc(
            UUID centreId, UUID userId);
}
