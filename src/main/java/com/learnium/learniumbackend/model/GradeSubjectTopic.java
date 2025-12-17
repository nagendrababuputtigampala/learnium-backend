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
@Table(name = "grade_subject_topic", uniqueConstraints = @UniqueConstraint(columnNames = {"grade_subject_id", "topic_id"}))
public class GradeSubjectTopic {

    @Id
    @Column(name = "gst_id")
    private Integer gstId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_subject_id")
    private GradeSubject gradeSubject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    @OneToMany(mappedBy = "gradeSubjectTopic")
    private List<TopicExamMode> topicExamModes;
}
