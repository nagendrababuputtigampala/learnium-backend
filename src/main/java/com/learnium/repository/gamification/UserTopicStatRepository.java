package com.learnium.repository.gamification;

import com.learnium.model.gamification.UserTopicStat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTopicStatRepository extends JpaRepository<UserTopicStat, UUID> {

    @Query(
            "select s from UserTopicStat s"
                    + " where s.centreId = :centreId"
                    + " and s.user.userId = :userId and s.topic.topicId = :topicId")
    Optional<UserTopicStat> findByCentreIdAndUserIdAndTopicId(
            @Param("centreId") UUID centreId,
            @Param("userId") UUID userId,
            @Param("topicId") UUID topicId);
}
