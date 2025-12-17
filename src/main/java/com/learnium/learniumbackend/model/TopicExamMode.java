package com.learnium.learniumbackend.model;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "topic_exam_mode", uniqueConstraints = @UniqueConstraint(columnNames = {"gst_id", "exam_mode_id"}))
public class TopicExamMode {

    @Id
    @Column(name = "topic_exam_mode_id")
    private Integer topicExamModeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gst_id")
    private GradeSubjectTopic gradeSubjectTopic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_mode_id")
    private ExamMode examMode;

    @OneToMany(mappedBy = "topicExamMode")
    private List<Exam> exams;
}