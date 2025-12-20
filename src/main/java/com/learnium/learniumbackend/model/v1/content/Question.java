package com.learnium.learniumbackend.model.v1.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.learniumbackend.model.v1.common.AuditableEntity;
import com.learnium.learniumbackend.model.v1.master.QuestionType;
import jakarta.persistence.*;
import java.util.UUID;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "questions",
        schema = "learnium",
        indexes = {
                @Index(name = "idx_questions_topic_active", columnList = "topic_id, is_active"),
                @Index(name = "idx_questions_topic_difficulty", columnList = "topic_id, difficulty, is_active")
        }
)
@Setter
@Getter
@NoArgsConstructor
public class Question extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "question_type_id", nullable = false)
    private QuestionType questionType;

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

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
