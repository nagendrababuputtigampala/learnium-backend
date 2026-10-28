package com.learnium.repository.gamification;

import com.learnium.model.gamification.UserBadges;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserBadgesRepository extends JpaRepository<UserBadges, UUID> {

    @Query(
            "select ub from UserBadges ub"
                    + " where ub.centreId = :centreId and ub.user.userId = :userId")
    List<UserBadges> findByCentreIdAndUserId(
            @Param("centreId") UUID centreId, @Param("userId") UUID userId);
}
