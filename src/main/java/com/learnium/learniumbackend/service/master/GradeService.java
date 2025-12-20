package com.learnium.learniumbackend.service.master;

import com.learnium.learniumbackend.entity.response.master.GradeResponse;
import com.learnium.learniumbackend.mapper.master.GradeMapper;
import com.learnium.learniumbackend.repository.v1.master.GradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GradeService {

    private final GradeRepository gradeRepository;
    private final GradeMapper gradeMapper;

    @Transactional(readOnly = true)
    public List<GradeResponse> getAllGrades() {
        return gradeMapper.toDtoList(gradeRepository.findAllActiveOrdered());
    }
}