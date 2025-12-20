package com.learnium.learniumbackend.mapper.master;

import com.learnium.learniumbackend.entity.response.master.SubjectResponse;
import com.learnium.learniumbackend.model.v1.content.GradeSubject;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        componentModel = "spring")
public interface SubjectMapper {

    @Mapping(target = "subjectId", source = "subject.subjectId")
    @Mapping(target = "subjectName", source = "subject.subjectName")
    @Mapping(target = "icon", source = "subject.icon")
    @Mapping(target = "color", source = "subject.color")
    @Mapping(target = "description", source = "subject.description")
    SubjectResponse toDto(GradeSubject subject);
    List<SubjectResponse> toDtoList(List<GradeSubject> subjects);
}
