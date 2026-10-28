package com.learnium.model.user;

import com.learnium.model.common.AuditableEntity;
import com.learnium.model.master.GradeSubject;
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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One teaching group inside a centre: a grade+subject pairing plus its roster. */
@Entity
@Table(
        name = "batches",
        schema = "learnium",
        indexes = {@Index(name = "idx_batches_centre", columnList = "centre_id")})
@Getter
@Setter
@NoArgsConstructor
public class Batch extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "batch_id", nullable = false, updatable = false)
    private UUID batchId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "centre_id", nullable = false)
    private Centre centre;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_subject_id", nullable = false)
    private GradeSubject gradeSubject;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
