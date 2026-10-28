package com.learnium.model.exam;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.model.common.AuditableEntity;
import com.learnium.model.content.Topic;
import com.learnium.model.master.ExamMode;
import com.learnium.model.master.Grade;
import com.learnium.model.master.Subject;
import com.learnium.model.user.Centre;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A reusable test blueprint owned by a centre; publishing freezes it into an ExamInstance. */
@Entity
@Table(
        name = "exam_templates",
        schema = "learnium",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_exam_templates_unique",
                    columnNames = {
                        "centre_id",
                        "grade_id",
                        "subject_id",
                        "topic_id",
                        "exam_mode_id",
                        "template_name",
                        "version"
                    })
        },
        indexes = {
            @Index(
                    name = "idx_exam_templates_lookup",
                    columnList = "grade_id, subject_id, topic_id, exam_mode_id, is_active, version"),
            @Index(name = "idx_exam_templates_centre", columnList = "centre_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class ExamTemplate extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "exam_template_id", nullable = false, updatable = false)
    private UUID examTemplateId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "centre_id", nullable = false)
    private Centre centre;

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
