package com.learnium.learniumbackend.entity.response.exam;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SubmitExamAttemptResponse {
    private UUID attemptId;
    private String status;

    private int totalQuestions;
    private int attempted;
    private int correct;
    private int wrong;
    private int skipped;

    private int score;
    private double percentage;

    private Instant submittedAt;
}