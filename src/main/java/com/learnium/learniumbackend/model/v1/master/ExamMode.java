package com.learnium.learniumbackend.model.v1.master;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.learniumbackend.model.v1.common.AuditableEntity;
import com.learnium.learniumbackend.model.v1.common.ExamModeType;
import jakarta.persistence.*;
import java.util.UUID;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "exam_modes",
        schema = "learnium",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_exam_modes_type_title", columnNames = {"test_type", "title"})
        },
        indexes = {
                @Index(name = "idx_exam_modes_active_sort", columnList = "is_active, sort_order")
        }
)
@Setter
@Getter
@NoArgsConstructor
public class ExamMode extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "exam_mode_id", nullable = false, updatable = false)
    private UUID examModeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_type", nullable = false, length = 30)
    private ExamModeType testType;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description")
    private String description;

    // UI fields
    @Column(name = "icon")
    private String icon;

    @Column(name = "button_text")
    private String buttonText;

    @Column(name = "button_color")
    private String buttonColor;

    @Column(name = "gradient_from")
    private String gradientFrom;

    @Column(name = "gradient_to")
    private String gradientTo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features", columnDefinition = "jsonb")
    private JsonNode features;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

}
