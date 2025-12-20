package com.learnium.learniumbackend.model.v1.gamification;

import com.learnium.learniumbackend.model.v1.common.AuditableEntity;
import com.learnium.learniumbackend.model.v1.content.Topic;
import com.learnium.learniumbackend.model.v1.user.UserAccount;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "user_topic_stats",
        schema = "learnium",
        uniqueConstraints = @UniqueConstraint(name="uq_user_topic_stats", columnNames={"user_id","topic_id"}),
        indexes = {
                @Index(name="idx_user_topic_stats_user", columnList="user_id"),
                @Index(name="idx_user_topic_stats_topic", columnList="topic_id")
        }
)
@Setter
@Getter
@NoArgsConstructor
public class UserTopicStat extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_topic_stats_id", nullable = false, updatable = false)
    private UUID userTopicStatsId;

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
