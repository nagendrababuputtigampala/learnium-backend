package com.learnium.repository.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.model.content.QuestionStatus;
import java.time.Instant;
import java.util.UUID;

/** Scalar projection deliberately excludes material text and file storage keys. */
public interface QuestionSummary {
    UUID getQuestionId();
    String getType();
    QuestionStatus getStatus();
    UUID getGradeSubjectId();
    String getGradeName();
    String getSubjectName();
    UUID getTopicId();
    String getTopicName();
    short getDifficulty();
    JsonNode getPayload();
    String getCorrectRef();
    long getRevision();
    int getVersion();
    UUID getCreatedBy();
    Instant getCreatedAt();
    UUID getApprovedBy();
    Instant getApprovedAt();
    UUID getSourceQuestionId();
    UUID getMaterialId();
    String getMaterialTitle();
    String getProvider();
    String getModel();
    String getPromptVersion();
    UUID getGenerationJobId();
}
