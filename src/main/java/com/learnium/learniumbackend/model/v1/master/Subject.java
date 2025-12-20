package com.learnium.learniumbackend.model.v1.master;

import com.learnium.learniumbackend.model.v1.common.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;
@Setter
@Getter
@Entity
@Table(
        name = "subjects",
        schema = "learnium",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_subjects_name", columnNames = {"subject_name"})
        },
        indexes = {
                @Index(name = "idx_subjects_active_sort", columnList = "is_active, sort_order")
        }
)
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
