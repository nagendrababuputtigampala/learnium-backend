package com.learnium.model.content;

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
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "topics",
        schema = "learnium",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_topics_name_per_grade_subject",
                    columnNames = {"grade_subject_id", "topic_name"})
        },
        indexes = {
            @Index(name = "idx_topics_grade_subject", columnList = "grade_subject_id, is_active, sort_order")
        })
@Getter
@Setter
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
