package com.learnium.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The per-type tallies of one executed job, persisted as the job's
 * {@code result_counts} JSON: for each type, how many were requested,
 * received from the model, accepted as drafts and dropped in validation
 * (with a tally of drop-reason codes). Partial success stays measurable:
 * quality is judged on these numbers, not on the job's status alone.
 */
public record GenerationResultCounts(EnumMap<GeneratedQuestionType, TypeResult> byType) {

    /** Tallies for one question type. received = accepted + dropped, always. */
    public record TypeResult(
            int requested,
            int received,
            int accepted,
            int dropped,
            Map<String, Integer> dropReasons) {

        public TypeResult {
            dropReasons = Map.copyOf(dropReasons);
        }

        static TypeResult zero(int requested) {
            return new TypeResult(requested, 0, 0, 0, Map.of());
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Empty tallies for a job about to execute: every type at its requested count. */
    public static GenerationResultCounts forRequest(Map<GeneratedQuestionType, Integer> requested) {
        EnumMap<GeneratedQuestionType, TypeResult> byType = new EnumMap<>(GeneratedQuestionType.class);
        for (GeneratedQuestionType type : GeneratedQuestionType.values()) {
            byType.put(type, TypeResult.zero(requested.getOrDefault(type, 0)));
        }
        return new GenerationResultCounts(byType);
    }

    public TypeResult forType(GeneratedQuestionType type) {
        return byType.get(type);
    }

    public int totalAccepted() {
        return byType.values().stream().mapToInt(TypeResult::accepted).sum();
    }

    /** Serializes to the persisted JSON shape (all five types, in enum order). */
    public JsonNode toJson() {
        ObjectNode root = JSON.createObjectNode();
        for (Map.Entry<GeneratedQuestionType, TypeResult> entry : byType.entrySet()) {
            TypeResult result = entry.getValue();
            ObjectNode node = root.putObject(entry.getKey().name());
            node.put("requested", result.requested());
            node.put("received", result.received());
            node.put("accepted", result.accepted());
            node.put("dropped", result.dropped());
            ObjectNode reasons = node.putObject("dropReasons");
            result.dropReasons().forEach(reasons::put);
        }
        return root;
    }

    /** Reads back the persisted JSON shape. */
    public static GenerationResultCounts fromJson(JsonNode json) {
        EnumMap<GeneratedQuestionType, TypeResult> byType = new EnumMap<>(GeneratedQuestionType.class);
        for (GeneratedQuestionType type : GeneratedQuestionType.values()) {
            JsonNode node = json.get(type.name());
            Map<String, Integer> reasons = new LinkedHashMap<>();
            JsonNode reasonNode = node.get("dropReasons");
            reasonNode
                    .properties()
                    .forEach(entry -> reasons.put(entry.getKey(), entry.getValue().asInt()));
            byType.put(
                    type,
                    new TypeResult(
                            node.get("requested").asInt(),
                            node.get("received").asInt(),
                            node.get("accepted").asInt(),
                            node.get("dropped").asInt(),
                            reasons));
        }
        return new GenerationResultCounts(byType);
    }
}
