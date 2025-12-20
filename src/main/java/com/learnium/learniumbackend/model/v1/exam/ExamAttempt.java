package com.learnium.learniumbackend.model.v1.exam;

import com.learnium.learniumbackend.model.v1.common.AuditableEntity;
import com.learnium.learniumbackend.model.v1.master.ExamMode;
import com.learnium.learniumbackend.model.v1.master.Grade;
import com.learnium.learniumbackend.model.v1.master.Subject;
import com.learnium.learniumbackend.model.v1.content.Topic;
import com.learnium.learniumbackend.model.v1.user.UserAccount;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "exam_attempts",
        schema = "learnium",
        indexes = {
                @Index(name = "idx_exam_attempts_user_time", columnList = "user_id, submitted_at"),
                @Index(name = "idx_exam_attempts_template_user", columnList = "exam_template_id, user_id"),
                @Index(name = "idx_exam_attempts_lookup", columnList = "grade_id, subject_id, topic_id, exam_mode_id, submitted_at")
        }
)
@Getter @Setter
@NoArgsConstructor
public class ExamAttempt extends AuditableEntity {

    @Id
    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId; // FE can generate this UUID

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_template_id", nullable = false)
    private ExamTemplate examTemplate;

    // Denormalized for fast dashboard queries
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id", nullable = false)
    private Grade grade;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private Topic topic; // nullable if template.topic is nullable

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_mode_id", nullable = false)
    private ExamMode examMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExamAttemptStatus status = ExamAttemptStatus.STARTED;

    @Column(name = "template_version", nullable = false)
    private int templateVersion;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "attempted_questions", nullable = false)
    private int attemptedQuestions;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "wrong_count", nullable = false)
    private int wrongCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "percentage", nullable = false)
    private double percentage;

    public void markSubmitted(Instant now) {
        this.status = ExamAttemptStatus.SUBMITTED;
        this.submittedAt = now;
    }
}
