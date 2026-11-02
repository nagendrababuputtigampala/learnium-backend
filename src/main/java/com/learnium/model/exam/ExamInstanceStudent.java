package com.learnium.model.exam;

import com.learnium.model.user.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "exam_instance_student", schema = "learnium", uniqueConstraints =
        @UniqueConstraint(name = "uq_instance_student", columnNames = {"exam_instance_id", "user_id"}))
@Getter @Setter @NoArgsConstructor
public class ExamInstanceStudent {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "centre_id", nullable = false)
    private UUID centreId;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_instance_id", nullable = false)
    private ExamInstance examInstance;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;
    @Column(name = "added_by", nullable = false)
    private UUID addedBy;
    @Column(name = "added_at", nullable = false)
    private Instant addedAt;
}
