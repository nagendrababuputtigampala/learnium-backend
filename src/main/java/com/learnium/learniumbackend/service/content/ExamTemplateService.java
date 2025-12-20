package com.learnium.learniumbackend.service.content;

import com.learnium.learniumbackend.entity.response.content.ExamTemplateResponse;
import com.learnium.learniumbackend.exception.ValidationException;
import com.learnium.learniumbackend.mapper.content.ExamTemplateMapper;
import com.learnium.learniumbackend.repository.v1.exam.ExamTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExamTemplateService {

    private final ExamTemplateRepository examTemplateRepository;
    private final ExamTemplateMapper examTemplateMapper;

    @Transactional(readOnly = true)
    public List<ExamTemplateResponse> getTests(UUID gradeId, UUID subjectId, UUID topicId, UUID examModeId) {
        if (gradeId == null) throw new ValidationException("gradeId is required");
        if (subjectId == null) throw new ValidationException("subjectId is required");
        if (topicId == null) throw new ValidationException("topicId is required");
        if (examModeId == null) throw new ValidationException("examModeId is required");

        return examTemplateMapper.toDtoList(
                examTemplateRepository.findActiveTemplates(gradeId, subjectId, topicId, examModeId)
        );
    }
}