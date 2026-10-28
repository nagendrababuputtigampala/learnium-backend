package com.learnium.model.user;

import com.learnium.model.common.AuditableEntity;
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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The batch roster — the gate for everything a student does. A student appears
 * here (with their PIN hash) before they can join any test in the batch; a
 * teacher appears here before they can run the batch.
 */
@Entity
@Table(
        name = "batch_memberships",
        schema = "learnium",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_batch_memberships_batch_user",
                    columnNames = {"batch_id", "user_id"})
        },
        indexes = {
            @Index(name = "idx_batch_memberships_batch_status", columnList = "batch_id, status"),
            @Index(name = "idx_batch_memberships_user", columnList = "user_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class BatchMembership extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "membership_id", nullable = false, updatable = false)
    private UUID membershipId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false, length = 20)
    private MemberRole memberRole;

    /** BCrypt hash of the student's roster PIN; null for teachers. */
    @Column(name = "pin_hash", length = 255)
    private String pinHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MembershipStatus status = MembershipStatus.ACTIVE;
}
