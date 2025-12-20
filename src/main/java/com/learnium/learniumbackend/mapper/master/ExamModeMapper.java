package com.learnium.learniumbackend.mapper.master;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnium.learniumbackend.entity.response.master.ExamModeResponse;
import com.learnium.learniumbackend.model.v1.master.ExamMode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public abstract class ExamModeMapper {

    @Mapping(target = "features", expression = "java(toStringList(entity.getFeatures()))")
    public abstract ExamModeResponse toDto(ExamMode entity);

    public abstract List<ExamModeResponse> toDtoList(List<ExamMode> entities);

    protected List<String> toStringList(JsonNode node) {
        if (node == null || node.isNull()) return Collections.emptyList();

        // features: ["a","b","c"]
        if (node.isArray()) {
            List<String> out = new ArrayList<>();
            node.forEach(n -> out.add(n.asText()));
            return out;
        }

        // features: {"items":["a","b"]}
        JsonNode items = node.get("items");
        if (items != null && items.isArray()) {
            List<String> out = new ArrayList<>();
            items.forEach(n -> out.add(n.asText()));
            return out;
        }

        // features: "single"
        if (node.isTextual()) return List.of(node.asText());

        return Collections.emptyList();
    }
}
