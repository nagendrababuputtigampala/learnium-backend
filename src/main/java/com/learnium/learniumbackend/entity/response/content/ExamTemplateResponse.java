package com.learnium.learniumbackend.entity.response.content;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamTemplateResponse {
    private UUID examTemplateId;
    private String templateName;          // e.g., "Addition Basics - Quiz 1"
    private String description;
    private int durationSeconds;   // or minutes if your UI uses that
    private int totalQuestions;
    private String difficulty;

    private int sortOrder;
}
