package com.learnium.learniumbackend.entity.response.exam;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ExamAttemptReviewResponse {

    private UUID attemptId;

    private UUID examTemplateId;
    private String templateName;
    private int templateVersion;

    private UUID gradeId;
    private UUID subjectId;
    private UUID topicId;
    private UUID examModeId;

    private String status;
    private Integer durationSec;

    private int totalQuestions;
    private int attempted;
    private int correct;
    private int wrong;
    private int skipped;

    private int score;
    private double percentage;

    private Instant startedAt;
    private Instant submittedAt;

    private List<QuestionReviewItem> questions;

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class QuestionReviewItem {
        private UUID questionAttemptId;
        private UUID questionId;

        private String questionType;     // from question_types.name
        private short difficulty;

        @JsonRawValue
        private String questionPayload;

        @JsonRawValue
        private String userAnswer;      // answer_payload

        private String correctOptionId;        // for MCQ
        private List<String> acceptedAnswers;

        private boolean correct;
        private short points;
        private Integer timeSpentSec;
    }
}