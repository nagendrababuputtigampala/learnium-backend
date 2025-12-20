package com.learnium.learniumbackend.repository.v1.user;

import java.util.Optional;
import java.util.UUID;

import com.learnium.learniumbackend.model.v1.user.StudentProfile;
import com.learnium.learniumbackend.repository.v1.BaseRepository;
import org.springframework.data.jpa.repository.Query;

public interface StudentProfileRepository extends BaseRepository<StudentProfile> {

    Optional<StudentProfile> findByUserId(UUID userId);

    @Query("""
        select sp
        from StudentProfile sp
        join fetch sp.grade g
        where sp.userId = :userId
    """)
    Optional<StudentProfile> findByUserIdWithGrade(UUID userId);
}