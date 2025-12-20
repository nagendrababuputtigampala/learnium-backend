package com.learnium.learniumbackend.service.master;

import com.learnium.learniumbackend.entity.response.master.ExamModeResponse;
import com.learnium.learniumbackend.exception.ValidationException;
import com.learnium.learniumbackend.mapper.master.ExamModeMapper;
import com.learnium.learniumbackend.repository.v1.master.ExamModeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExamModeService {

    private final ExamModeRepository examModeRepository;
    private final ExamModeMapper examModeMapper;

    @Transactional(readOnly = true)
    public List<ExamModeResponse> getAllExamModes() {
        return examModeMapper.toDtoList(examModeRepository.findActiveOrdered());
    }

    @Transactional(readOnly = true)
    public List<ExamModeResponse> getExamModes(UUID gradeId, UUID subjectId, UUID topicId) {
        if (gradeId == null && subjectId == null && topicId == null) {
            return examModeMapper.toDtoList(examModeRepository.findActiveOrdered());
        }

        if (gradeId == null || subjectId == null || topicId == null) {
            throw new ValidationException("gradeId, subjectId, and topicId are required together");
        }

        return examModeMapper.toDtoList(examModeRepository.findAvailableModes(gradeId, subjectId, topicId));
    }
}