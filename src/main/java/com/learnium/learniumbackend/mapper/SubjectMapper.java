package com.learnium.learniumbackend.mapper;

import com.learnium.learniumbackend.model.Subject;
import com.learnium.learniumbackend.entity.response.SubjectResponse;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(unmappedSourcePolicy = ReportingPolicy.IGNORE, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface SubjectMapper {

    SubjectMapper INSTANCE = Mappers.getMapper(SubjectMapper.class);

    @Mapping(target = "subjectId", source = "subjectId")
    @Mapping(target = "subjectName", source = "subjectName")
    SubjectResponse toSubjectResponse(Subject subject);

    List<SubjectResponse> toSubjectResponseList(List<Subject> subjects);
}

