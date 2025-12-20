package com.learnium.learniumbackend.repository.v1.master;

import java.util.List;
import java.util.Optional;

import com.learnium.learniumbackend.model.v1.master.Subject;
import com.learnium.learniumbackend.repository.v1.BaseRepository;
import org.springframework.data.jpa.repository.Query;

public interface SubjectRepository extends BaseRepository<Subject> {

    Optional<Subject> findBySubjectNameIgnoreCase(String subjectName);

    @Query("""
        select s
        from Subject s
        where s.active = true
        order by s.sortOrder asc, s.subjectName asc
    """)
    List<Subject> findActiveOrdered();
}