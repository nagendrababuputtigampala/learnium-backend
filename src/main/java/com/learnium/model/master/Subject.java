package com.learnium.model.master;

import com.learnium.model.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "subjects",
        schema = "learnium",
        uniqueConstraints = {
            @UniqueConstraint(name = "uq_subjects_name", columnNames = {"subject_name"})
        },
        indexes = {
            @Index(name = "idx_subjects_active_sort", columnList = "is_active, sort_order")
        })
@NoArgsConstructor
public class Subject extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @Column(name = "subject_name", nullable = false, length = 120)
    private String subjectName;

    @Column(name = "description")
    private String description;

    // UI columns
    @Column(name = "icon")
    private String icon;

    @Column(name = "color")
    private String color;

    @Column(name = "gradient_from")
    private String gradientFrom;

    @Column(name = "gradient_to")
    private String gradientTo;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
