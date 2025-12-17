package com.learnium.learniumbackend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "grade")
@Getter
@Setter
@NoArgsConstructor
public class Grade {

    @Id
    @Column(columnDefinition = "grade_id", updatable = false, nullable = false)
    private Integer gradeId;

    @Column(name = "grade_name", nullable = false, unique = true)
    private String gradeName;

    @OneToMany(mappedBy = "grade")
    private List<User> users;

    @OneToMany(mappedBy = "grade")
    private List<GradeSubject> courses;
}