package com.learnium.learniumbackend.repository.v1.gamification;

import com.learnium.learniumbackend.model.v1.gamification.UserTopicStat;
import com.learnium.learniumbackend.repository.v1.BaseRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserTopicStatRepository extends BaseRepository<UserTopicStat> {

    Optional<UserTopicStat> findByUser_UserIdAndTopic_TopicId(UUID userId, UUID topicId);

    List<UserTopicStat> findByUser_UserId(UUID userId);
}