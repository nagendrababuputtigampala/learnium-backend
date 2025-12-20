package com.learnium.learniumbackend.model.v1.content;
import com.learnium.learniumbackend.model.v1.common.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
        name = "topics",
        schema = "learnium",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_topics_name_per_grade_subject", columnNames = {"grade_subject_id", "topic_name"})
        },
        indexes = {
                @Index(name = "idx_topics_grade_subject", columnList = "grade_subject_id, is_active, sort_order")
        }
)
@Setter
@Getter
@NoArgsConstructor
public class Topic extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "topic_id", nullable = false, updatable = false)
    private UUID topicId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_subject_id", nullable = false)
    private GradeSubject gradeSubject;

    @Column(name = "topic_name", nullable = false, length = 200)
    private String topicName;

    @Column(name = "description")
    private String description;

    // Optional UI fields
    @Column(name = "icon")
    private String icon;

    @Column(name = "color")
    private String color;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
