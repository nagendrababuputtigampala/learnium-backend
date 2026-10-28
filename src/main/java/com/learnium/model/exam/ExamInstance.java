package com.learnium.model.exam;

import com.learnium.model.common.AuditableEntity;
import com.learnium.model.user.Batch;
import com.learnium.model.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One publishing of an {@link ExamTemplate} to one batch: the join code, the
 * open/close window, and the integrity/camera settings for that sitting.
 *
 * <p><b>Semantic rename vs the legacy model:</b> in legacy-demo, "ExamInstance"
 * was one student's run of a template. Here it is the batch-level published
 * test; the per-student run is {@link ExamAttempt}, which points at this
 * instance. See ER-Diagram.md, "Deliberate changes from the legacy-demo model".
 */
@Entity
@Table(
        name = "exam_instances",
        schema = "learnium",
        uniqueConstraints = {
            @UniqueConstraint(name = "uq_exam_instances_join_code", columnNames = {"join_code"})
        },
        indexes = {
            @Index(name = "idx_exam_instances_batch", columnList = "batch_id, status"),
            @Index(name = "idx_exam_instances_template", columnList = "exam_template_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class ExamInstance extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "exam_instance_id", nullable = false, updatable = false)
    private UUID examInstanceId;

    /** Owning centre, copied from the batch; composite FKs keep it honest (V5). */
    @Column(name = "centre_id", nullable = false)
    private UUID centreId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_template_id", nullable = false)
    private ExamTemplate examTemplate;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by", nullable = false)
    private UserAccount publishedBy;

    /** Per-test join code; regenerable by the teacher. */
    @Column(name = "join_code", nullable = false, length = 20)
    private String joinCode;

    @Column(name = "opens_at")
    private Instant opensAt;

    @Column(name = "closes_at")
    private Instant closesAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExamInstanceStatus status = ExamInstanceStatus.PUBLISHED;

    @Column(name = "integrity_signals_on", nullable = false)
    private boolean integritySignalsOn = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "camera_mode", nullable = false, length = 20)
    private CameraMode cameraMode = CameraMode.OFF;

    @Column(name = "retakes_allowed", nullable = false)
    private boolean retakesAllowed = false;
}
