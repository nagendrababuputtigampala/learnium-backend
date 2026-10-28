package com.learnium.model.master;

import com.learnium.model.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "grades",
        schema = "learnium",
        indexes = {@Index(name = "idx_grades_active_sort", columnList = "is_active, sort_order")})
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
