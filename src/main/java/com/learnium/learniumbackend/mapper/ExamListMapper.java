package com.learnium.learniumbackend.mapper;

import com.learnium.learniumbackend.entity.response.ExamListResponse;
import com.learnium.learniumbackend.entity.response.ExamModeDetails;
import com.learnium.learniumbackend.model.Exam;
import com.learnium.learniumbackend.model.ExamMode;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(unmappedSourcePolicy = ReportingPolicy.IGNORE, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface ExamListMapper {

    ExamListMapper INSTANCE = Mappers.getMapper(ExamListMapper.class);

    @Mapping(target = "examId", source = "examId")
    @Mapping(target = "examName", source = "examName")
    ExamListResponse toExamDetails(Exam exam);

    List<ExamListResponse> toExamList(List<Exam> exams);
}
