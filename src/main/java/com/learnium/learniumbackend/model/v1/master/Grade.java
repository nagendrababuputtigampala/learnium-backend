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
        name = "grades",
        schema = "learnium",
        indexes = {
                @Index(name = "idx_grades_active_sort", columnList = "is_active, sort_order")
        }
)
@NoArgsConstructor
public class Grade extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "grade_id", nullable = false, updatable = false)
    private UUID gradeId;

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code; // G1..G12

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

}
