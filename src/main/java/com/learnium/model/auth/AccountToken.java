package com.learnium.model.auth;

import com.learnium.model.common.AuditableEntity;
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

/**
 * A single-use, expiring account token (teacher invite or password reset). Only
 * the SHA-256 hash of the raw token is stored — the raw value travels in the
 * emailed link and is never persisted or logged.
 */
@Entity
@Table(
        name = "account_token",
        schema = "learnium",
        indexes = {
            @Index(name = "idx_account_token_hash", columnList = "token_hash"),
            @Index(name = "idx_account_token_user", columnList = "user_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class AccountToken extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "account_token_id", nullable = false, updatable = false)
    private UUID accountTokenId;

    /** Owning centre, copied from the user; composite FK keeps it honest (V7). */
    @Column(name = "centre_id", nullable = false)
    private UUID centreId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20)
    private AccountTokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Set when the token is consumed; a used token can never be replayed. */
    @Column(name = "used_at")
    private Instant usedAt;
}
