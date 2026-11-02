package com.learnium.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.learnium.admin.TopicCreationService.CreatedTopic;
import com.learnium.api.dto.AdminDtos.TopicDto;
import com.learnium.model.content.Topic;
import com.learnium.repository.content.TopicRepository;
import com.learnium.repository.master.GradeSubjectRepository;
import com.learnium.repository.user.BatchRepository;
import com.learnium.repository.user.CentreRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The topic create-or-return paths an HTTP test cannot reach
 * deterministically: the creation-race recovery — an insert that loses to
 * the (grade_subject_id, topic_name) unique constraint must come back as
 * the winning topic, never as an error — and the rethrow when the
 * violation turns out to be about something else.
 */
class CatalogServiceTest {

    private final GradeSubjectRepository gradeSubjectRepository =
            mock(GradeSubjectRepository.class);
    private final TopicRepository topicRepository = mock(TopicRepository.class);
    private final CentreRepository centreRepository = mock(CentreRepository.class);
    private final BatchRepository batchRepository = mock(BatchRepository.class);
    private final TopicCreationService topicCreationService = mock(TopicCreationService.class);
    private final CatalogService service =
            new CatalogService(
                    gradeSubjectRepository,
                    topicRepository,
                    centreRepository,
                    batchRepository,
                    topicCreationService);

    @Test
    void createTopicTrimsTheNameAndReturnsTheCreation() {
        UUID gradeSubjectId = UUID.randomUUID();
        var created = new CreatedTopic(new TopicDto(UUID.randomUUID(), "Fractions"), true);
        when(topicCreationService.insert(gradeSubjectId, "Fractions")).thenReturn(created);

        CreatedTopic result = service.createTopic(gradeSubjectId, "  Fractions  ");

        assertThat(result).isEqualTo(created);
        verify(topicCreationService).insert(gradeSubjectId, "Fractions");
    }

    @Test
    void createTopicReturnsTheWinnerWhenTheInsertLosesTheRace() {
        UUID gradeSubjectId = UUID.randomUUID();
        UUID winnerId = UUID.randomUUID();
        Topic winner = new Topic();
        winner.setTopicId(winnerId);
        winner.setTopicName("Fractions");
        when(topicCreationService.insert(gradeSubjectId, "Fractions"))
                .thenThrow(new DataIntegrityViolationException("uq_topics_name_per_grade_subject"));
        when(topicRepository.findByGradeSubjectIdAndTopicName(gradeSubjectId, "Fractions"))
                .thenReturn(Optional.of(winner));

        CreatedTopic result = service.createTopic(gradeSubjectId, "Fractions");

        assertThat(result.created()).isFalse();
        assertThat(result.topic()).isEqualTo(new TopicDto(winnerId, "Fractions"));
    }

    @Test
    void createTopicRethrowsWhenThereIsNoWinner() {
        UUID gradeSubjectId = UUID.randomUUID();
        var conflict = new DataIntegrityViolationException("some other constraint");
        when(topicCreationService.insert(gradeSubjectId, "Fractions")).thenThrow(conflict);
        when(topicRepository.findByGradeSubjectIdAndTopicName(gradeSubjectId, "Fractions"))
                .thenReturn(Optional.empty());

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> service.createTopic(gradeSubjectId, "Fractions"))
                .isSameAs(conflict);
    }
}
