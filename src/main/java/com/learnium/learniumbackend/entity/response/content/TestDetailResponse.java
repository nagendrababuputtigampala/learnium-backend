package com.learnium.learniumbackend.entity.response.content;


import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TestDetailResponse {
    private UUID examTemplateId;
    private String templateName;
    private String description;
    private int durationSeconds;
    private int totalQuestions;

    private UUID gradeId;
    private UUID subjectId;
    private UUID topicId;
    private UUID examModeId;
    private int version;

    private List<QuestionResponse> questions;
}
