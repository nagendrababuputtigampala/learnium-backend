package com.learnium.learniumbackend.mapper.master;

import com.learnium.learniumbackend.entity.response.master.GradeResponse;
import com.learnium.learniumbackend.model.v1.master.Grade;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface GradeMapper {
    GradeResponse toDto(Grade grade);

    List<GradeResponse> toDtoList(List<Grade> grades);
}