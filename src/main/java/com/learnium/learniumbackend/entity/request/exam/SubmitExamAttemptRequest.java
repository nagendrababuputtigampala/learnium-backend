package com.learnium.learniumbackend.entity.request.exam;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SubmitExamAttemptRequest {

    private UUID examTemplateId;
    private UUID userId;
    private Integer durationSec;

    @Builder.Default
    private List<AnswerItem> answers = List.of();

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class AnswerItem {
        private UUID questionId;
        private Map<String, Object> answer;
        private Integer timeSpentSec;
    }
}