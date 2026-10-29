package com.learnium.model.content;

/** Lifecycle of a {@link BackgroundJob}: queued, claimed, done (either way). */
public enum BackgroundJobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED
}
