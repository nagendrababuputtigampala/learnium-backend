package com.learnium.generation;

import com.learnium.model.content.GenerationDifficulty;
import java.util.EnumMap;
import java.util.Map;

/**
 * Everything one generation call needs, built by the worker from the
 * job's frozen inputs plus the material's stored text. Provider-neutral:
 * the same request goes to Gemini or Ollama, and the response contract
 * (see {@link GenerationSchema}) is identical for both.
 */
public record GenerationRequest(
        String sourceText,
        String topicName,
        String gradeName,
        String subjectName,
        Map<GeneratedQuestionType, Integer> counts,
        GenerationDifficulty difficulty,
        String payloadSchemaVersion) {

    public GenerationRequest {
        EnumMap<GeneratedQuestionType, Integer> copy = new EnumMap<>(GeneratedQuestionType.class);
        copy.putAll(counts);
        counts = copy;
    }

    /** How many questions of one type were requested (0 when absent). */
    public int countFor(GeneratedQuestionType type) {
        return counts.getOrDefault(type, 0);
    }

    public int totalRequested() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }
}
