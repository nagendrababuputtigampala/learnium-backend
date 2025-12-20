package com.learnium.learniumbackend.model.v1.exam;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.learniumbackend.model.v1.common.AuditableEntity;
import com.learnium.learniumbackend.model.v1.content.Question;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(
        name = "question_attempts",
        schema = "learnium",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_question_attempts_attempt_question", columnNames = {"attempt_id", "question_id"})
        },
        indexes = {
                @Index(name = "idx_question_attempts_attempt", columnList = "attempt_id"),
                @Index(name = "idx_question_attempts_question", columnList = "question_id")
        }
)
@Getter @Setter
@NoArgsConstructor
public class QuestionAttempt extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "question_attempt_id", nullable = false, updatable = false)
    private UUID questionAttemptId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ExamAttempt attempt;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "question_version", nullable = false)
    private int questionVersion;

    @Column(name = "difficulty", nullable = false)
    private short difficulty;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer_payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode answerPayload;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "time_spent_sec")
    private Integer timeSpentSec;

    @Column(name = "points", nullable = false)
    private short points = 1;
}