package com.learnium.learniumbackend.model.v1.exam;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.learniumbackend.model.v1.common.AuditableEntity;
import com.learnium.learniumbackend.model.v1.content.Topic;
import com.learnium.learniumbackend.model.v1.master.ExamMode;
import com.learnium.learniumbackend.model.v1.master.Grade;
import com.learnium.learniumbackend.model.v1.master.Subject;
import jakarta.persistence.*;
import java.util.UUID;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "exam_templates",
        schema = "learnium",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_exam_templates_unique",
                        columnNames = {"grade_id","subject_id","topic_id","exam_mode_id","template_name","version"}
                )
        },
        indexes = {
                @Index(name = "idx_exam_templates_lookup", columnList = "grade_id, subject_id, topic_id, exam_mode_id, is_active, version")
        }
)
@Setter
@Getter
@NoArgsConstructor
public class ExamTemplate extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "exam_template_id", nullable = false, updatable = false)
    private UUID examTemplateId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id", nullable = false)
    private Grade grade;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private Topic topic; // nullable

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_mode_id", nullable = false)
    private ExamMode examMode;

    @Column(name = "template_name", nullable = false, length = 120)
    private String templateName;

    @Column(name = "description")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules", nullable = false, columnDefinition = "jsonb")
    private JsonNode rules;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
