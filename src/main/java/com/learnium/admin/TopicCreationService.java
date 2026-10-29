package com.learnium.admin;

import com.learnium.api.dto.AdminDtos.TopicDto;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.content.Topic;
import com.learnium.model.master.GradeSubject;
import com.learnium.repository.content.TopicRepository;
import com.learnium.repository.master.GradeSubjectRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional half of topic creation, kept apart from
 * {@link CatalogService} for the same reason as material creation: if the
 * insert loses a race against the (grade_subject_id, topic_name) unique
 * constraint, the violation must roll back HERE, inside this transaction,
 * so the caller can recover by returning the winning row — after a
 * constraint violation the surrounding transaction is unusable.
 *
 * <p>The case-insensitive lookup happens inside this transaction,
 * immediately before the insert, and the insert is flushed before
 * returning, so the unique constraint — not the lookup — is the final
 * arbiter when two identical creates race.
 */
@Service
public class TopicCreationService {

    private final GradeSubjectRepository gradeSubjectRepository;
    private final TopicRepository topicRepository;

    public TopicCreationService(
            GradeSubjectRepository gradeSubjectRepository, TopicRepository topicRepository) {
        this.gradeSubjectRepository = gradeSubjectRepository;
        this.topicRepository = topicRepository;
    }

    /** The outcome of a create-or-return: the topic, and whether this call created it. */
    public record CreatedTopic(TopicDto topic, boolean created) {}

    @Transactional
    public CreatedTopic insert(UUID gradeSubjectId, String name) {
        GradeSubject gradeSubject =
                gradeSubjectRepository
                        .findById(gradeSubjectId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.SYS_NOT_FOUND));
        var existing =
                topicRepository.findByGradeSubjectIdAndTopicNameIgnoreCase(gradeSubjectId, name);
        if (!existing.isEmpty()) {
            Topic topic = existing.get(0);
            return new CreatedTopic(new TopicDto(topic.getTopicId(), topic.getTopicName()), false);
        }
        Topic topic = new Topic();
        topic.setGradeSubject(gradeSubject);
        topic.setTopicName(name);
        topic = topicRepository.save(topic);
        // Force the insert out so a uniqueness violation surfaces here, as
        // a DataIntegrityViolationException the caller recovers from,
        // instead of at some later commit it cannot.
        topicRepository.flush();
        return new CreatedTopic(new TopicDto(topic.getTopicId(), topic.getTopicName()), true);
    }
}
