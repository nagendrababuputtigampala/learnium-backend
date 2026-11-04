package com.learnium.model.exam;

import com.learnium.model.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A frozen student progress report (M6). The facts JSON is computed from attempt
 * facts at generation time and never changes afterwards; regeneration writes a
 * new row, and readers take the latest for the student + scope.
 */
@Entity
@Table(
        name = "student_reports",
        schema = "learnium",
        indexes = {@Index(name = "idx_student_reports_student", columnList = "centre_id, user_id, generated_at")})
@Getter
@Setter
@NoArgsConstructor
public class StudentReport extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "report_id", nullable = false, updatable = false)
    private UUID reportId;

    @Column(name = "centre_id", nullable = false, updatable = false)
    private UUID centreId;

    /** The student the report is about. */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Optional scope: the batch the report was generated for; null = all the student's batches. */
    @Column(name = "batch_id", updatable = false)
    private UUID batchId;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "facts", nullable = false, columnDefinition = "jsonb")
    private String facts;

    @Column(name = "teacher_comment", length = 2000)
    private String teacherComment;

    @Column(name = "generated_by", nullable = false, updatable = false)
    private UUID generatedBy;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;
}
