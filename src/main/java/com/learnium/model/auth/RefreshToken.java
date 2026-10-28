package com.learnium.model.auth;

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

/**
 * A rotating refresh token. Only the SHA-256 hash of the raw token is stored.
 * Every refresh revokes the presented row and issues a fresh one; presenting an
 * already-revoked row is treated as token reuse (possible theft) and revokes
 * every token of that user.
 */
@Entity
@Table(
        name = "refresh_token",
        schema = "learnium",
        uniqueConstraints = {
            @UniqueConstraint(name = "uq_refresh_token_hash", columnNames = {"token_hash"})
        },
        indexes = {
            @Index(name = "idx_refresh_token_hash", columnList = "token_hash"),
            @Index(name = "idx_refresh_token_user", columnList = "user_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "refresh_token_id", nullable = false, updatable = false)
    private UUID refreshTokenId;

    /** Owning centre, copied from the user; composite FK keeps it honest (V7). */
    @Column(name = "centre_id", nullable = false)
    private UUID centreId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Set on rotation, logout, password reset or reuse detection. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
