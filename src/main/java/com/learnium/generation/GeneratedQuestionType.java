package com.learnium.generation;

import com.learnium.model.master.QuestionType;

/**
 * The five generatable question kinds. Each constant's name is the
 * {@link QuestionType} master-data code, so a job's per-type counts key
 * directly off these names in JSON and in the API.
 */
public enum GeneratedQuestionType {
    MCQ,
    FILL_BLANK,
    FLASHCARD,
    NUMERIC,
    WRITTEN
}
