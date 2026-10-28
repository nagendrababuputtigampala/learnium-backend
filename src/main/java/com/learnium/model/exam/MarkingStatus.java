package com.learnium.model.exam;

/**
 * Marking lifecycle of one answer. Objective types are AUTO; written answers sit
 * at MARKING_PENDING until a teacher confirms the AI-suggested score (CONFIRMED).
 * Only confirmed written marks feed totals.
 */
public enum MarkingStatus {
    AUTO,
    MARKING_PENDING,
    CONFIRMED
}
