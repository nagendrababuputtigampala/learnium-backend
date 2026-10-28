package com.learnium.model.auth;

/** Delivery lifecycle of an {@link EmailOutbox} row. */
public enum EmailOutboxStatus {
    PENDING,
    SENT,
    FAILED
}
