package com.learnium.model.user;

import com.learnium.model.common.AuditableEntity;
import com.learnium.model.common.UserStatus;
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
 * One login/person inside a centre. Admins and teachers authenticate with
 * email + password hash; students have neither — their credential is the roster
 * PIN on {@link BatchMembership}. (The legacy Firebase auth_provider/auth_uid
 * columns are dropped: v0.1.0 uses its own JWT.)
 */
@Entity
@Table(
        name = "user_account",
        schema = "learnium",
        // Staff-email uniqueness is GLOBAL and case-insensitive, enforced by
        // the Flyway V8 expression index uq_user_account_email_global on
        // lower(email) — an expression index cannot be declared as a JPA
        // unique constraint, so it lives only in the migration.
        indexes = {
            @Index(name = "idx_user_account_centre", columnList = "centre_id"),
            @Index(name = "idx_user_account_email", columnList = "email")
        })
@Getter
@Setter
@NoArgsConstructor
public class UserAccount extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "centre_id", nullable = false)
    private Centre centre;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "display_name", length = 150)
    private String displayName;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    /**
     * Credential epoch (Flyway V9): incremented in the same transaction as
     * every password set/change/reset and every deactivation. Refresh tokens
     * are stamped with the version at issuance and rotation rejects a stale
     * stamp — that is what makes a login racing a password reset harmless:
     * whenever its token was written, it carries the pre-reset version.
     */
    @Column(name = "security_version", nullable = false)
    private long securityVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
