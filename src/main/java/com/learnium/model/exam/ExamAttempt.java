package com.learnium.model.exam;

import com.learnium.model.common.AuditableEntity;
import com.learnium.model.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One student's sitting of one published instance (the per-student run the
 * legacy model called ExamInstance). {@code attemptNo} distinguishes retakes;
 * (instance, student, attemptNo) is unique.
 */
@Entity
@Table(
        name = "exam_attempts",
        schema = "learnium",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_exam_attempts_instance_user_no",
                    columnNames = {"exam_instance_id", "user_id", "attempt_no"})
        },
        indexes = {
            @Index(name = "idx_exam_attempts_instance", columnList = "exam_instance_id"),
            @Index(name = "idx_exam_attempts_user", columnList = "user_id, submitted_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class ExamAttempt extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_instance_id", nullable = false)
    private ExamInstance examInstance;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExamAttemptStatus status = ExamAttemptStatus.STARTED;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions = 0;

    @Column(name = "correct_count", nullable = false)
    private int correctCount = 0;

    /** Null until the attempt is submitted and totals are known. */
    @Column(name = "total_score")
    private BigDecimal totalScore;

    @Column(name = "integrity_flag_count", nullable = false)
    private int integrityFlagCount = 0;
}
