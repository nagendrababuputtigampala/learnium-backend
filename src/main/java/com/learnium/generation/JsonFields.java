package com.learnium.generation;

import com.fasterxml.jackson.databind.JsonNode;

/** Null-safe readers for provider response JSON (missing/null → null, never an exception). */
final class JsonFields {

    private JsonFields() {}

    static String textOrNull(JsonNode parent, String field) {
        JsonNode node = parent == null ? null : parent.get(field);
        return node == null || !node.isTextual() ? null : node.asText();
    }

    static Long longOrNull(JsonNode parent, String field) {
        JsonNode node = parent == null ? null : parent.get(field);
        return node == null || !node.isNumber() ? null : node.asLong();
    }

    static JsonNode childOrNull(JsonNode parent, String field) {
        JsonNode node = parent == null ? null : parent.get(field);
        return node == null || node.isNull() ? null : node;
    }
}
