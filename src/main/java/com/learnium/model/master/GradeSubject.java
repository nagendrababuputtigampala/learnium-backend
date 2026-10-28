package com.learnium.model.master;

import com.learnium.model.common.AuditableEntity;
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

/** A grade+subject pairing — the unit a batch teaches and topics hang off. */
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
        })
@Getter
@Setter
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
