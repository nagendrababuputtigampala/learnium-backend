package com.learnium.learniumbackend.model.v1.master;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "question_types", schema = "learnium")
@Setter
@Getter
@NoArgsConstructor
public class QuestionType {

    @Id
    @Column(name = "question_type_id", nullable = false)
    private Short questionTypeId;

    @Column(name = "name", nullable = false, unique = true, length = 60)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

}
