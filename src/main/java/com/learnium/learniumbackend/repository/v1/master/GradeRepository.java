package com.learnium.learniumbackend.repository.v1.master;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.learnium.learniumbackend.model.v1.master.Grade;
import com.learnium.learniumbackend.repository.v1.BaseRepository;
import org.springframework.data.jpa.repository.Query;

public interface GradeRepository extends BaseRepository<Grade> {

    Optional<Grade> findByCode(String code);

    @Query("""
        select g
        from Grade g
        where g.active = true
        order by g.sortOrder asc, g.name asc
    """)
    List<Grade> findAllActiveOrdered();

    @Query("""
        select g.gradeId
        from Grade g
        where g.code = :code
    """)
    Optional<UUID> findIdByCode(String code);
}
