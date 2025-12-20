package com.learnium.learniumbackend.model.v1.exam;

import com.learnium.learniumbackend.model.v1.common.AuditableEntity;
import com.learnium.learniumbackend.model.v1.common.ExamInstanceStatus;
import com.learnium.learniumbackend.model.v1.user.UserAccount;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "exam_instances",
        schema = "learnium",
        indexes = {
                @Index(name = "idx_exam_instances_user_time", columnList = "user_id, started_at"),
                @Index(name = "idx_exam_instances_template", columnList = "exam_template_id, started_at"),
                @Index(name = "idx_exam_instances_status", columnList = "status")
        }
)
@Setter
@Getter
@NoArgsConstructor
public class ExamInstance extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "exam_instance_id", nullable = false, updatable = false)
    private UUID examInstanceId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_template_id", nullable = false)
    private ExamTemplate examTemplate;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExamInstanceStatus status = ExamInstanceStatus.IN_PROGRESS;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions = 0;

    @Column(name = "total_correct", nullable = false)
    private int totalCorrect = 0;

    @Column(name = "total_score", nullable = false)
    private int totalScore = 0;

    @Column(name = "total_time_ms", nullable = false)
    private int totalTimeMs = 0;
}
