package com.learnium.learniumbackend.model.v1.exam;

import com.learnium.learniumbackend.model.v1.common.AuditableEntity;
import com.learnium.learniumbackend.model.v1.content.Question;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
        name = "exam_instance_questions",
        schema = "learnium",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_exam_instance_question", columnNames = {"exam_instance_id","question_id"}),
                @UniqueConstraint(name = "uq_exam_instance_order", columnNames = {"exam_instance_id","question_order"})
        },
        indexes = {
                @Index(name = "idx_exam_instance_questions_instance", columnList = "exam_instance_id, question_order")
        }
)
@Setter
@Getter
@NoArgsConstructor
public class ExamInstanceQuestion extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "exam_instance_question_id", nullable = false, updatable = false)
    private UUID examInstanceQuestionId;

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

}