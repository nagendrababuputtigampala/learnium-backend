package com.learnium.learniumbackend.util;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class QuestionPayloadSanitizer {

    /**
     * Removes any answer keys so FE never receives correct answers.
     * Adjust keys to match your schema.
     */
    public JsonNode sanitize(JsonNode payload) {
        if (payload == null || payload.isNull()) return payload;
        if (!payload.isObject()) return payload;

        ObjectNode obj = ((ObjectNode) payload).deepCopy();

        // Remove common answer fields
        obj.remove("correctOptionId");
        obj.remove("correctOptionIndex");
        obj.remove("correctAnswer");
        obj.remove("answer");
        obj.remove("solution");
        obj.remove("explanation"); // optional (keep if you want)

        // If options contain per-option "isCorrect"
        JsonNode options = obj.get("options");
        if (options != null && options.isArray()) {
            options.forEach(node -> {
                if (node != null && node.isObject()) {
                    ((ObjectNode) node).remove("isCorrect");
                    ((ObjectNode) node).remove("correct");
                }
            });
        }

        return obj;
    }
}
