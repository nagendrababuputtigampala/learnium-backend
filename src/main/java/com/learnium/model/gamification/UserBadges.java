package com.learnium.model.gamification;

import com.learnium.model.common.AuditableEntity;
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

@Entity
@Table(
        name = "user_badges",
        schema = "learnium",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_badges", columnNames = {"user_id", "badge_id"}),
        indexes = @Index(name = "idx_user_badges_user", columnList = "user_id, awarded_at"))
@Getter
@Setter
@NoArgsConstructor
public class UserBadges extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_badge_id", nullable = false, updatable = false)
    private UUID userBadgeId;

    /** Owning centre, copied from the user; composite FKs keep it honest (V5). */
    @Column(name = "centre_id", nullable = false)
    private UUID centreId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "badge_id", nullable = false)
    private Badge badge;

    @Column(name = "awarded_at", nullable = false)
    private Instant awardedAt = Instant.now();
}
