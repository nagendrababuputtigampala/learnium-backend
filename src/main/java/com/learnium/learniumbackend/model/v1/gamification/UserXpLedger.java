package com.learnium.learniumbackend.model.v1.gamification;

import com.learnium.learniumbackend.model.v1.common.AuditableEntity;
import com.learnium.learniumbackend.model.v1.common.XpSourceType;
import com.learnium.learniumbackend.model.v1.user.UserAccount;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "user_xp_ledger",
        schema = "learnium",
        indexes = @Index(name="idx_xp_user_time", columnList="user_id, earned_at")
)
@Setter
@Getter
@NoArgsConstructor
public class UserXpLedger extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "xp_ledger_id", nullable = false, updatable = false)
    private UUID xpLedgerId;

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