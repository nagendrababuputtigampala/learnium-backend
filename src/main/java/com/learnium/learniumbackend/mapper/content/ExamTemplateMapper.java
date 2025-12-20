package com.learnium.learniumbackend.mapper.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.learniumbackend.entity.response.content.ExamTemplateResponse;
import com.learnium.learniumbackend.model.v1.exam.ExamTemplate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ExamTemplateMapper {

    @Mapping(target = "durationSeconds", source = "rules", qualifiedByName = "extractDurationSeconds")
    @Mapping(target = "totalQuestions", source = "rules", qualifiedByName = "extractTotalQuestions")
    @Mapping(target = "difficulty",source = "rules", qualifiedByName = "extractDifficulty")
    ExamTemplateResponse toDto(ExamTemplate entity);

    List<ExamTemplateResponse> toDtoList(List<ExamTemplate> entities);

    @Named("extractDurationSeconds")
    default Integer extractDurationSeconds(JsonNode rules) {
        if (rules != null && rules.has("time_limit_sec")) {
            return rules.get("time_limit_sec").asInt();
        }
        return null;
    }

    @Named("extractTotalQuestions")
    default Integer extractTotalQuestions(JsonNode rules) {
        if (rules != null && rules.has("total_questions")) {
            return rules.get("total_questions").asInt();
        }
        return null;
    }

    @Named("extractDifficulty")
    default String extractDifficulty(JsonNode rules) {
        if (rules != null && rules.has("difficulty")) {
            return rules.get("difficulty").asText();
        }
        return null;
    }

}