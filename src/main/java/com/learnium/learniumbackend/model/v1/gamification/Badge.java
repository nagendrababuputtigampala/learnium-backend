package com.learnium.learniumbackend.model.v1.gamification;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.learniumbackend.model.v1.common.AuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "badges", schema = "learnium",
        uniqueConstraints = @UniqueConstraint(name="uq_badges_code", columnNames={"code"}),
        indexes = @Index(name="idx_badges_active", columnList="is_active")
)
@Setter
@Getter
@NoArgsConstructor
public class Badge extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "badge_id", nullable = false, updatable = false)
    private UUID badgeId;

    @Column(name = "code", nullable = false, length = 80)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "icon")
    private String icon;

    @Column(name = "color")
    private String color;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule", nullable = false, columnDefinition = "jsonb")
    private JsonNode rule;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}