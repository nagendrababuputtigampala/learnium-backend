package com.learnium.learniumbackend.mapper;

import com.learnium.learniumbackend.entity.response.Grades;
import com.learnium.learniumbackend.model.Grade;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(unmappedSourcePolicy = ReportingPolicy.IGNORE, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface GradeMapper {

    GradeMapper INSTANCE = Mappers.getMapper(GradeMapper.class);

    @Mapping(target = "gradeId", source = "gradeId")
    @Mapping(target = "gradeName", source = "gradeName")
    Grades toResponse(Grade grade);

    List<Grades> toResponseList(List<Grade> grades);
}
