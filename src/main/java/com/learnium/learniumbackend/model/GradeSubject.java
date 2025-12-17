package com.learnium.learniumbackend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;


@Entity
@Table(name = "grade_subject", uniqueConstraints = @UniqueConstraint(columnNames = {"grade_id", "subject_id"}))
@Getter
@Setter
@NoArgsConstructor
public class GradeSubject {

    @Id
    @Column(name = "grade_subject_id")
    private Integer gradeSubjectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    private Grade grade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @OneToMany(mappedBy = "gradeSubject")
    private List<GradeSubjectTopic> topics;

}
