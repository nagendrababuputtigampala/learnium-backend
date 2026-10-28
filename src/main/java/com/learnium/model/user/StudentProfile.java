package com.learnium.model.user;

import com.learnium.model.common.AuditableEntity;
import com.learnium.model.master.Grade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Student-only details, sharing the primary key of the owning {@link UserAccount}. */
@Entity
@Table(
        name = "student_profiles",
        schema = "learnium",
        indexes = {@Index(name = "idx_student_profiles_grade", columnList = "grade_id")})
@Getter
@Setter
@NoArgsConstructor
public class StudentProfile extends AuditableEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Owning centre, copied from the user; composite FKs keep it honest (V5). */
    @Column(name = "centre_id", nullable = false)
    private UUID centreId;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id", nullable = false)
    private Grade grade;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "dob")
    private LocalDate dob;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "locale")
    private String locale;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
