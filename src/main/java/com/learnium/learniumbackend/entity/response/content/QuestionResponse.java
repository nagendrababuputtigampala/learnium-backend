package com.learnium.learniumbackend.entity.response.content;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;

import java.util.UUID;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class QuestionResponse {
    private UUID questionId;

    @JsonRawValue
    private String payload;
}