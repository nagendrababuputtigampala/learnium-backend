package com.learnium.model.exam;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.model.common.AuditableEntity;
import com.learnium.model.content.Question;
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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One answer inside an {@link ExamAttempt}. Objective types carry
 * {@code isCorrect}; written answers carry the AI-suggested score and stay
 * MARKING_PENDING until a teacher sets {@code confirmedScore}.
 */
@Entity
@Table(
        name = "question_attempts",
        schema = "learnium",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_question_attempts_attempt_question",
                    columnNames = {"attempt_id", "question_id"})
        },
        indexes = {
            @Index(name = "idx_question_attempts_attempt", columnList = "attempt_id"),
            @Index(name = "idx_question_attempts_question", columnList = "question_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class QuestionAttempt extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "question_attempt_id", nullable = false, updatable = false)
    private UUID questionAttemptId;

    /** Owning centre, copied from the attempt; composite FKs keep it honest (V5). */
    @Column(name = "centre_id", nullable = false)
    private UUID centreId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ExamAttempt attempt;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "question_version", nullable = false)
    private int questionVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer_payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode answerPayload;

    /** Objective types only; null for written answers. */
    @Column(name = "is_correct")
    private Boolean correct;

    /** Written answers only: the AI's suggested mark, set by the grading flow. */
    @Column(name = "ai_suggested_score")
    private BigDecimal aiSuggestedScore;

    /** Written answers only: the teacher-confirmed mark — the only one that counts. */
    @Column(name = "confirmed_score")
    private BigDecimal confirmedScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "marking_status", nullable = false, length = 20)
    private MarkingStatus markingStatus = MarkingStatus.AUTO;

    @Column(name = "marker_feedback", columnDefinition = "text")
    private String markerFeedback;

    @Column(name = "points", nullable = false)
    private short points = 1;
}
