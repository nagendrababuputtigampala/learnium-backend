package com.learnium.repository.gamification;

import com.learnium.model.gamification.UserBadges;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserBadgesRepository extends JpaRepository<UserBadges, UUID> {

    @Query("select ub from UserBadges ub where ub.user.userId = :userId")
    List<UserBadges> findByUserId(@Param("userId") UUID userId);
}
