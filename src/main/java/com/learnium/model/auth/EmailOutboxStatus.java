package com.learnium.model.auth;

/** Delivery lifecycle of an {@link EmailOutbox} row. */
public enum EmailOutboxStatus {
    PENDING,
    /** Claimed by one dispatcher pass; a claim older than the stale window is reclaimable. */
    SENDING,
    SENT,
    FAILED
}
