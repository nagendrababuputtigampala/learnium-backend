package com.learnium.learniumbackend.model.v1.user;

import com.learnium.learniumbackend.model.v1.common.AuditableEntity;
import com.learnium.learniumbackend.model.v1.master.Grade;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "student_profiles",
        schema = "learnium",
        indexes = {
                @Index(name = "idx_student_profiles_grade", columnList = "grade_id")
        }
)
@Setter
@Getter
@NoArgsConstructor
public class StudentProfile extends AuditableEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

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
