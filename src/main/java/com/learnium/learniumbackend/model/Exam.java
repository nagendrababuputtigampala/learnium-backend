package com.learnium.learniumbackend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "exam", uniqueConstraints = @UniqueConstraint(columnNames = {"topic_exam_mode_id", "exam_name"}))
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer examId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_exam_mode_id", nullable = false)
    private TopicExamMode topicExamMode;

    @Column(name = "exam_name", nullable = false)
    private String examName;

    @Column(name = "exam_order", nullable = false)
    private Integer examOrder;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}