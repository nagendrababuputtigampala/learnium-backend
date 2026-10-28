package com.learnium.model.master;

import com.learnium.model.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Master list of question kinds (MCQ, FILL_BLANK, FLASHCARD, NUMERIC, WRITTEN),
 * seeded by migration with fixed UUIDs. Ported from the legacy model, which used
 * a Short id and no code column; the ER model gives it a UUID id plus a stable code.
 */
@Entity
@Table(name = "question_types", schema = "learnium")
@Getter
@Setter
@NoArgsConstructor
public class QuestionType extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "question_type_id", nullable = false, updatable = false)
    private UUID questionTypeId;

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
