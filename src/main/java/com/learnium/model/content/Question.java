package com.learnium.model.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.model.common.AuditableEntity;
import com.learnium.model.master.QuestionType;
import com.learnium.model.user.Centre;
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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One question in a centre's bank. The type-specific shape (options, model
 * answer, rubric, numeric tolerance) lives in the JSONB payload; correctRef
 * points into it. AI-generated questions start DRAFT with material set; manual
 * questions have no material. The creator is the audit {@code created_by}
 * column (populated by JPA auditing), as in the legacy model.
 */
@Entity
@Table(
        name = "questions",
        schema = "learnium",
        indexes = {
            @Index(name = "idx_questions_topic_active", columnList = "topic_id, is_active"),
            @Index(name = "idx_questions_topic_difficulty", columnList = "topic_id, difficulty, is_active"),
            @Index(name = "idx_questions_centre_status", columnList = "centre_id, status")
        })
@Getter
@Setter
@NoArgsConstructor
public class Question extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "centre_id", nullable = false)
    private Centre centre;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "question_type_id", nullable = false)
    private QuestionType questionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id")
    private Material material; // nullable — set when AI-generated from a material

    @Column(name = "difficulty", nullable = false)
    private short difficulty = 1; // 1..5

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    // IMPORTANT: never send to FE
    @Column(name = "correct_ref", nullable = false, length = 50)
    private String correctRef;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private QuestionStatus status = QuestionStatus.DRAFT;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
