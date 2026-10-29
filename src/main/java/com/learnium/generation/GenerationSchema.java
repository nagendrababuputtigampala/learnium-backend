package com.learnium.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The structured-output schema handed to the provider (Gemini
 * {@code responseSchema}, Ollama {@code format}). It expresses the
 * payload contract loosely — one item object with a type tag and every
 * type's fields optional — because the strict per-type rules live in
 * server-side validation, which also produces the drop reasons. The
 * schema's job is to keep the model's output shaped enough to parse.
 */
public final class GenerationSchema {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonNode SCHEMA = build();

    private GenerationSchema() {}

    public static JsonNode schema() {
        return SCHEMA;
    }

    private static JsonNode build() {
        ObjectNode item = JSON.createObjectNode();
        item.put("type", "object");
        ObjectNode properties = item.putObject("properties");
        ObjectNode typeProperty = properties.putObject("type");
        typeProperty.put("type", "string");
        ArrayNode typeEnum = typeProperty.putArray("enum");
        for (GeneratedQuestionType type : GeneratedQuestionType.values()) {
            typeEnum.add(type.name());
        }
        stringProperty(properties, "stem");
        ObjectNode options = properties.putObject("options");
        options.put("type", "array");
        ObjectNode optionItem = options.putObject("items");
        optionItem.put("type", "object");
        stringProperty(optionItem.putObject("properties"), "text");
        optionItem.putArray("required").add("text");
        properties.putObject("correctIndex").put("type", "integer");
        stringProperty(properties, "stemWithBlank");
        stringProperty(properties, "answer");
        stringProperty(properties, "front");
        stringProperty(properties, "back");
        properties.putObject("answerValue").put("type", "number");
        properties.putObject("tolerance").put("type", "number");
        stringProperty(properties, "unit");
        stringProperty(properties, "workedSolution");
        stringProperty(properties, "modelAnswer");
        ObjectNode rubric = properties.putObject("rubric");
        rubric.put("type", "array");
        ObjectNode rubricItem = rubric.putObject("items");
        rubricItem.put("type", "object");
        ObjectNode rubricProperties = rubricItem.putObject("properties");
        stringProperty(rubricProperties, "criterion");
        rubricProperties.putObject("maxScore").put("type", "number");
        ArrayNode rubricRequired = rubricItem.putArray("required");
        rubricRequired.add("criterion");
        rubricRequired.add("maxScore");
        item.putArray("required").add("type");

        ObjectNode root = JSON.createObjectNode();
        root.put("type", "object");
        ObjectNode rootProperties = root.putObject("properties");
        ObjectNode questions = rootProperties.putObject("questions");
        questions.put("type", "array");
        questions.set("items", item);
        root.putArray("required").add("questions");
        return root;
    }

    private static void stringProperty(ObjectNode properties, String name) {
        properties.putObject(name).put("type", "string");
    }
}
