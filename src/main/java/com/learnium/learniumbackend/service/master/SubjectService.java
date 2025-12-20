package com.learnium.learniumbackend.service.master;

import com.learnium.learniumbackend.entity.response.master.SubjectResponse;
import com.learnium.learniumbackend.exception.ValidationException;
import com.learnium.learniumbackend.mapper.master.SubjectMapper;
import com.learnium.learniumbackend.model.v1.master.Grade;
import com.learnium.learniumbackend.repository.v1.content.GradeSubjectRepository;
import com.learnium.learniumbackend.repository.v1.master.GradeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private final GradeRepository gradeRepository;
    private final GradeSubjectRepository gradeSubjectRepository;
    private final SubjectMapper subjectMapper;

    @Transactional
    public List<SubjectResponse> getSubjectsByGrade(String gradeCode) {
        if (StringUtils.isBlank(gradeCode)) {
            throw new ValidationException("gradeCode is required");
        }
        Grade grade = gradeRepository.findByCode(gradeCode)
                .orElseThrow(() -> new ValidationException("Unknown gradeCode: " + gradeCode));

        return subjectMapper.toDtoList(
                gradeSubjectRepository.findActiveSubjectsByGrade(grade.getGradeId())
        );
    }
}