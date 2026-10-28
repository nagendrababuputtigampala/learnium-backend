package com.learnium.model.gamification;

import com.learnium.model.common.AuditableEntity;
import com.learnium.model.common.XpSourceType;
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
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "user_xp_ledger",
        schema = "learnium",
        indexes = @Index(name = "idx_xp_user_time", columnList = "user_id, earned_at"))
@Getter
@Setter
@NoArgsConstructor
public class UserXpLedger extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "xp_ledger_id", nullable = false, updatable = false)
    private UUID xpLedgerId;

    /** Owning centre, copied from the user; composite FKs keep it honest (V5). */
    @Column(name = "centre_id", nullable = false)
    private UUID centreId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private XpSourceType sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "xp_delta", nullable = false)
    private int xpDelta;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt = Instant.now();
}
