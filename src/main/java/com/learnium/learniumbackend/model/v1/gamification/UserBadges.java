package com.learnium.learniumbackend.model.v1.gamification;
import com.learnium.learniumbackend.model.v1.common.AuditableEntity;
import com.learnium.learniumbackend.model.v1.user.UserAccount;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "user_badges",
        schema = "learnium",
        uniqueConstraints = @UniqueConstraint(name="uq_user_badges", columnNames={"user_id","badge_id"}),
        indexes = @Index(name="idx_user_badges_user", columnList="user_id, awarded_at")
)
@Setter
@Getter
@NoArgsConstructor
public class UserBadges extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_badge_id", nullable = false, updatable = false)
    private UUID userBadgeId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "badge_id", nullable = false)
    private Badge badge;

    @Column(name = "awarded_at", nullable = false)
    private Instant awardedAt = Instant.now();

}
