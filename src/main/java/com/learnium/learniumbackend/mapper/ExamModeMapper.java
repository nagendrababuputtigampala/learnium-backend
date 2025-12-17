package com.learnium.learniumbackend.mapper;

import com.learnium.learniumbackend.entity.response.ExamModeDetails;
import com.learnium.learniumbackend.entity.response.Grades;
import com.learnium.learniumbackend.entity.response.UserDetails;
import com.learnium.learniumbackend.model.ExamMode;
import com.learnium.learniumbackend.model.Grade;
import com.learnium.learniumbackend.model.User;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(unmappedSourcePolicy = ReportingPolicy.IGNORE, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface ExamModeMapper {

    ExamModeMapper INSTANCE = Mappers.getMapper(ExamModeMapper.class);

    @Mapping(target = "examModeId", source = "examModeId")
    @Mapping(target = "modeName", source = "modeName")
    ExamModeDetails toExamModeDetails(ExamMode examMode);

    List<ExamModeDetails> toExamModeDetailsList(List<ExamMode> examModes);
}
