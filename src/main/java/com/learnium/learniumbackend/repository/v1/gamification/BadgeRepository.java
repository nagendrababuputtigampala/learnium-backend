package com.learnium.learniumbackend.repository.v1.gamification;

import java.util.List;
import java.util.Optional;

import com.learnium.learniumbackend.model.v1.gamification.Badge;
import com.learnium.learniumbackend.repository.v1.BaseRepository;
import org.springframework.data.jpa.repository.Query;

public interface BadgeRepository extends BaseRepository<Badge> {
    Optional<Badge> findByCode(String code);

    @Query("""
        select b
        from Badge b
        where b.active = true
        order by b.name asc
    """)
    List<Badge> findActive();
}