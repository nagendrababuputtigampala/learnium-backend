package com.learnium.learniumbackend.model.v1.content;

import com.learnium.learniumbackend.model.v1.common.AuditableEntity;
import com.learnium.learniumbackend.model.v1.content.Question;
import com.learnium.learniumbackend.model.v1.exam.ExamTemplate;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
        name = "exam_template_questions",
        schema = "learnium",
        uniqueConstraints = {
                // prevents same question being added twice to same template (for same question_version)
                @UniqueConstraint(
                        name = "uq_etq_template_question_version",
                        columnNames = {"exam_template_id", "question_id", "question_version"}
                ),
                // prevents display_order duplicates inside a template (clean UI/ordering)
                @UniqueConstraint(
                        name = "uq_etq_template_display_order",
                        columnNames = {"exam_template_id", "display_order"}
                )
        },
        indexes = {
                @Index(name = "idx_etq_template_active_order", columnList = "exam_template_id, is_active, display_order"),
                @Index(name = "idx_etq_question", columnList = "question_id")
        }
)
@Setter
@Getter
@NoArgsConstructor
public class ExamTemplateQuestion extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "exam_template_question_id", nullable = false, updatable = false)
    private UUID examTemplateQuestionId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_template_id", nullable = false)
    private ExamTemplate examTemplate;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    /**
     * Snapshot of question version at time of linking.
     * Helps keep templates reproducible even if question payload evolves later.
     */
    @Column(name = "question_version", nullable = false)
    private int questionVersion = 1;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /**
     * Optional scoring weight per question (useful later).
     */
    @Column(name = "points", nullable = false)
    private short points = 1;

    /**
     * Optional: group into sections like "Warmup", "Core", "Challenge"
     */
    @Column(name = "section", length = 60)
    private String section;

    @Column(name = "is_required", nullable = false)
    private boolean required = true;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @PrePersist
    @PreUpdate
    private void syncQuestionVersion() {
        // If you always want to snapshot from Question.version automatically:
        if (this.question != null) {
            this.questionVersion = this.question.getVersion();
        }
    }
}