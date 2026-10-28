package com.learnium.model.content;

/** Approval lifecycle of a question: AI drafts land as DRAFT; only APPROVED questions reach students. */
public enum QuestionStatus {
    DRAFT,
    APPROVED,
    REJECTED
}
