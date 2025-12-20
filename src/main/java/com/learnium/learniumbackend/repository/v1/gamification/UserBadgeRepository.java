package com.learnium.learniumbackend.repository.v1.gamification;

import java.util.List;
import java.util.UUID;

import com.learnium.learniumbackend.model.v1.gamification.UserBadges;
import com.learnium.learniumbackend.repository.v1.BaseRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserBadgeRepository extends BaseRepository<UserBadges> {

    @Query("""
        select ub
        from UserBadges ub
        join fetch ub.badge b
        where ub.user.userId = :userId
        order by ub.awardedAt desc
    """)
    List<UserBadges> findUserBadges(UUID userId);

    boolean existsByUser_UserIdAndBadge_BadgeId(UUID userId, UUID badgeId);
}