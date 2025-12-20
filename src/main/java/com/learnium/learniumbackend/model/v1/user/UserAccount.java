package com.learnium.learniumbackend.model.v1.user;

import com.learnium.learniumbackend.model.v1.common.AuditableEntity;
import com.learnium.learniumbackend.model.v1.common.AuthProvider;
import com.learnium.learniumbackend.model.v1.common.UserStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "users",
        schema = "learnium",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_users_auth", columnNames = {"auth_provider", "auth_uid"})
        },
        indexes = {
                @Index(name = "idx_users_email", columnList = "email")
        }
)
@Setter
@Getter
@NoArgsConstructor
public class UserAccount extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 30)
    private AuthProvider authProvider;

    @Column(name = "auth_uid", nullable = false, length = 200)
    private String authUid;

    @Column(name = "email")
    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

}
