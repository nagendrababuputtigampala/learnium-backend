package com.learnium.model.exam;

import com.learnium.model.common.AuditableEntity;
import com.learnium.model.content.Question;
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

/** The frozen question set of a published instance: question + version pinned at publish time. */
@Entity
@Table(
        name = "exam_instance_questions",
        schema = "learnium",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_exam_instance_question",
                    columnNames = {"exam_instance_id", "question_id"}),
            @UniqueConstraint(
                    name = "uq_exam_instance_order",
                    columnNames = {"exam_instance_id", "question_order"})
        },
        indexes = {
            @Index(name = "idx_exam_instance_questions_instance", columnList = "exam_instance_id, question_order")
        })
@Getter
@Setter
@NoArgsConstructor
public class ExamInstanceQuestion extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "exam_instance_question_id", nullable = false, updatable = false)
    private UUID examInstanceQuestionId;

    /** Owning centre, copied from the instance; composite FKs keep it honest (V5). */
    @Column(name = "centre_id", nullable = false)
    private UUID centreId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_instance_id", nullable = false)
    private ExamInstance examInstance;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "question_order", nullable = false)
    private int questionOrder;

    @Column(name = "question_version", nullable = false)
    private int questionVersion = 1;

    @Column(name = "points", nullable = false)
    private short points = 1;
}
