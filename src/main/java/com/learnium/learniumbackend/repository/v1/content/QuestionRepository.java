package com.learnium.learniumbackend.repository.v1.content;

import java.util.List;
import java.util.UUID;

import com.learnium.learniumbackend.model.v1.content.Question;
import com.learnium.learniumbackend.repository.v1.BaseRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface QuestionRepository extends BaseRepository<Question> {

    @Query("""
        select q
        from Question q
        where q.topic.topicId = :topicId
          and q.active = true
        order by q.createdAt desc
    """)
    List<Question> findActiveByTopic(UUID topicId);

    @Query("""
        select q
        from Question q
        where q.topic.topicId = :topicId
          and q.active = true
          and q.difficulty between :minDiff and :maxDiff
        order by q.createdAt desc
    """)
    List<Question> findActiveByTopicAndDifficultyRange(UUID topicId, short minDiff, short maxDiff, Pageable pageable);

    @Query("""
        select q
        from Question q
        join fetch q.questionType qt
        where q.questionId in :questionIds
    """)
    List<Question> findAllByIdsWithType(List<UUID> questionIds);

    List<Question> findByQuestionIdIn(List<UUID> ids);

}
