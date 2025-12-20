package com.learnium.learniumbackend.model.v1.content;


import com.learnium.learniumbackend.model.v1.common.AuditableEntity;
import com.learnium.learniumbackend.model.v1.master.Grade;
import com.learnium.learniumbackend.model.v1.master.Subject;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
        name = "grade_subjects",
        schema = "learnium",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_grade_subject", columnNames = {"grade_id", "subject_id"})
        },
        indexes = {
                @Index(name = "idx_grade_subjects_grade", columnList = "grade_id, is_active, sort_order"),
                @Index(name = "idx_grade_subjects_subject", columnList = "subject_id")
        }
)
@Setter
@Getter
@NoArgsConstructor
public class GradeSubject extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "grade_subject_id", nullable = false, updatable = false)
    private UUID gradeSubjectId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id", nullable = false)
    private Grade grade;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
