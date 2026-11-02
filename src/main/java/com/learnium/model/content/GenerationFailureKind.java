package com.learnium.model.content;

/**
 * Why a generation job failed. The kind decides retry behaviour in the
 * worker: MODEL_ERROR, MALFORMED_RESPONSE and TRUNCATED are transient
 * (bounded retry); SAFETY_REFUSAL, ZERO_VALID and MATERIAL_DELETED are
 * terminal — retrying the same frozen inputs cannot change them.
 */
public enum GenerationFailureKind {
    MODEL_ERROR,
    MALFORMED_RESPONSE,
    SAFETY_REFUSAL,
    TRUNCATED,
    ZERO_VALID,
    MATERIAL_DELETED
}
