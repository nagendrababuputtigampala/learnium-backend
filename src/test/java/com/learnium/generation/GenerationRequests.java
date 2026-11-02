package com.learnium.generation;

import com.learnium.model.content.GenerationDifficulty;
import java.util.EnumMap;
import java.util.Map;

/** Shared request fixtures for the generation unit tests. */
final class GenerationRequests {

    private GenerationRequests() {}

    static GenerationRequest request(Map<GeneratedQuestionType, Integer> counts) {
        return new GenerationRequest(
                "Fractions represent parts of a whole. One half equals two quarters.",
                "Fractions",
                "Grade 8",
                "Mathematics",
                counts,
                GenerationDifficulty.MEDIUM,
                GenerationContract.PAYLOAD_SCHEMA_VERSION);
    }

    static GenerationRequest oneMcq() {
        EnumMap<GeneratedQuestionType, Integer> counts = new EnumMap<>(GeneratedQuestionType.class);
        counts.put(GeneratedQuestionType.MCQ, 1);
        return request(counts);
    }
}
