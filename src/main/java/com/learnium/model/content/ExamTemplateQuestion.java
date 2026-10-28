package com.learnium.model.content;

import com.learnium.model.common.AuditableEntity;
import com.learnium.model.exam.ExamTemplate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One blueprint row of an {@link ExamTemplate}: the question plus the version
 * pinned when it was linked, so later question edits never change a built test.
 * (The legacy entity auto-synced the version via a lifecycle callback; the port
 * sets it explicitly at link time instead.)
 */
@Entity
@Table(
        name = "exam_template_questions",
        schema = "learnium",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_etq_template_question_version",
                    columnNames = {"exam_template_id", "question_id", "question_version"}),
            @UniqueConstraint(
                    name = "uq_etq_template_display_order",
                    columnNames = {"exam_template_id", "display_order"})
        },
        indexes = {
            @Index(name = "idx_etq_template_active_order", columnList = "exam_template_id, is_active, display_order"),
            @Index(name = "idx_etq_question", columnList = "question_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class ExamTemplateQuestion extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "exam_template_question_id", nullable = false, updatable = false)
    private UUID examTemplateQuestionId;

    /** Owning centre, copied from the template; composite FKs keep it honest (V5). */
    @Column(name = "centre_id", nullable = false)
    private UUID centreId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_template_id", nullable = false)
    private ExamTemplate examTemplate;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    /** Snapshot of question version at time of linking. */
    @Column(name = "question_version", nullable = false)
    private int questionVersion = 1;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** Optional scoring weight per question. */
    @Column(name = "points", nullable = false)
    private short points = 1;

    /** Optional grouping into sections like "Warmup", "Core", "Challenge". */
    @Column(name = "section", length = 60)
    private String section;

    @Column(name = "is_required", nullable = false)
    private boolean required = true;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
