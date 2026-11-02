package com.learnium.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.model.content.GenerationFailureKind;

/**
 * The result of one provider call, before validation. A success carries
 * the raw {@code questions} array (validation decides what survives) and
 * the provider's token usage; a failure is classified so the worker can
 * apply the retry policy without knowing provider details. Failures
 * carry an exception class name only — never a message, which could
 * quote source text or a provider response.
 */
public sealed interface GenerationOutcome {

    record Success(
            JsonNode questions,
            Long usageInputTokens,
            Long usageOutputTokens,
            Long usageThinkingTokens,
            String providerRequestId)
            implements GenerationOutcome {}

    record Failure(GenerationFailureKind kind, String errorClass) implements GenerationOutcome {}
}
