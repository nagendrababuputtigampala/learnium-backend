package com.learnium.model.user;

import com.learnium.model.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A tutoring centre — the tenancy root. Every tenant-owned row traces back to one centre. */
@Entity
@Table(
        name = "centres",
        schema = "learnium",
        indexes = {@Index(name = "idx_centres_status", columnList = "status")})
@Getter
@Setter
@NoArgsConstructor
public class Centre extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "centre_id", nullable = false, updatable = false)
    private UUID centreId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CentreStatus status = CentreStatus.ACTIVE;
}
