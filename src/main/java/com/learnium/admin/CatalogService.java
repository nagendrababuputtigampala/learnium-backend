package com.learnium.admin;

import com.learnium.api.dto.AdminDtos.GradeSubjectDto;
import com.learnium.api.dto.AdminDtos.PublicBatchDto;
import com.learnium.api.dto.AdminDtos.PublicCentreDto;
import com.learnium.api.dto.AdminDtos.TopicDto;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.user.CentreStatus;
import com.learnium.repository.content.TopicRepository;
import com.learnium.repository.master.GradeSubjectRepository;
import com.learnium.repository.user.BatchRepository;
import com.learnium.repository.user.CentreRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only catalogues: the staff grade-subject list and the low-sensitivity
 * public pickers behind the student join page (centre and batch NAMES only —
 * never roster data).
 */
@Service
public class CatalogService {

    private final GradeSubjectRepository gradeSubjectRepository;
    private final TopicRepository topicRepository;
    private final CentreRepository centreRepository;
    private final BatchRepository batchRepository;

    public CatalogService(
            GradeSubjectRepository gradeSubjectRepository,
            TopicRepository topicRepository,
            CentreRepository centreRepository,
            BatchRepository batchRepository) {
        this.gradeSubjectRepository = gradeSubjectRepository;
        this.topicRepository = topicRepository;
        this.centreRepository = centreRepository;
        this.batchRepository = batchRepository;
    }

    @Transactional(readOnly = true)
    public List<GradeSubjectDto> gradeSubjects() {
        return gradeSubjectRepository.findAllForListing().stream()
                .map(
                        gs ->
                                new GradeSubjectDto(
                                        gs.getGradeSubjectId(),
                                        gs.getGrade().getCode(),
                                        gs.getGrade().getName(),
                                        gs.getSubject().getSubjectId(),
                                        gs.getSubject().getSubjectName(),
                                        gs.getGrade().getName() + " · " + gs.getSubject().getSubjectName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TopicDto> topics(UUID gradeSubjectId) {
        if (!gradeSubjectRepository.existsById(gradeSubjectId)) {
            throw new BusinessException(ErrorCode.SYS_NOT_FOUND);
        }
        return topicRepository.findByGradeSubjectIdOrderByTopicNameAsc(gradeSubjectId).stream()
                .map(topic -> new TopicDto(topic.getTopicId(), topic.getTopicName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicCentreDto> publicCentres() {
        return centreRepository.findByStatusOrderByNameAsc(CentreStatus.ACTIVE).stream()
                .map(centre -> new PublicCentreDto(centre.getCentreId(), centre.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicBatchDto> publicBatches(UUID centreId) {
        return batchRepository.findByCentreId(centreId).stream()
                .filter(batch -> batch.isActive())
                .sorted(Comparator.comparing(batch -> batch.getName()))
                .map(batch -> new PublicBatchDto(batch.getBatchId(), batch.getName()))
                .toList();
    }
}
