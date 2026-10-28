package com.learnium.model.gamification;

import com.learnium.model.common.AuditableEntity;
import com.learnium.model.content.Topic;
import com.learnium.model.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Per-student per-topic aggregate — the weak-topic dashboard reads these rows. */
@Entity
@Table(
        name = "user_topic_stats",
        schema = "learnium",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_topic_stats", columnNames = {"user_id", "topic_id"}),
        indexes = {
            @Index(name = "idx_user_topic_stats_user", columnList = "user_id"),
            @Index(name = "idx_user_topic_stats_topic", columnList = "topic_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class UserTopicStat extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_topic_stats_id", nullable = false, updatable = false)
    private UUID userTopicStatsId;

    /** Owning centre, copied from the user; composite FKs keep it honest (V5). */
    @Column(name = "centre_id", nullable = false)
    private UUID centreId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "correct", nullable = false)
    private int correct = 0;

    @Column(name = "avg_time_ms", nullable = false)
    private int avgTimeMs = 0;

    @Column(name = "xp_total", nullable = false)
    private int xpTotal = 0;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;
}
