package com.learnium.learniumbackend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "exam_mode")
public class ExamMode {

    @Id
    @Column(name = "exam_mode_id")
    private Integer examModeId;

    @Column(name = "mode_name", nullable = false)
    private String modeName;

    @OneToMany(mappedBy = "examMode")
    private List<TopicExamMode> topicExamModes;

}
