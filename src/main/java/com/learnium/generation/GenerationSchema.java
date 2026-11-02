package com.learnium.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/**
 * Shared structured-output schema (Gemini {@code responseSchema},
 * Ollama {@code format}). Both providers use the request-specific overload
 * to constrain types, required fields and array sizes during decoding.
 * The base schema supplies the field definitions; server-side validation
 * still enforces semantic rules and produces drop reasons.
 */
public final class GenerationSchema {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonNode SCHEMA = build();

    private GenerationSchema() {}

    public static JsonNode schema() {
        return SCHEMA;
    }

    /** Bound the answer to the requested types and size, including nested arrays. */
    public static JsonNode schema(GenerationRequest request) {
        ObjectNode root = SCHEMA.deepCopy();
        ObjectNode questions = (ObjectNode) root.path("properties").path("questions");
        questions.put("minItems", request.totalRequested());
        questions.put("maxItems", request.totalRequested());
        ArrayNode variants = JSON.createArrayNode();
        for (GeneratedQuestionType type : GeneratedQuestionType.values()) {
            if (request.countFor(type) <= 0) {
                continue;
            }
            List<String> fields = switch (type) {
                case MCQ -> List.of("type", "stem", "options", "correctIndex");
                case FILL_BLANK -> List.of("type", "stemWithBlank", "answer");
                case FLASHCARD -> List.of("type", "front", "back");
                case NUMERIC -> List.of("type", "stem", "answerValue", "tolerance", "unit", "workedSolution");
                case WRITTEN -> List.of("type", "stem", "modelAnswer", "rubric");
            };
            ObjectNode item = JSON.createObjectNode();
            item.put("type", "object");
            ObjectNode properties = item.putObject("properties");
            ArrayNode required = item.putArray("required");
            ArrayNode ordering = item.putArray("propertyOrdering");
            for (String field : fields) {
                properties.set(field, SCHEMA.path("properties").path("questions")
                        .path("items").path("properties").path(field).deepCopy());
                required.add(field);
                ordering.add(field);
            }
            ((ObjectNode) properties.get("type")).putArray("enum").add(type.name());
            if (type == GeneratedQuestionType.MCQ) {
                ((ObjectNode) properties.get("options")).put("minItems", 4).put("maxItems", 4);
                ((ObjectNode) properties.get("correctIndex")).put("minimum", 0).put("maximum", 3);
            }
            if (type == GeneratedQuestionType.WRITTEN) {
                ((ObjectNode) properties.get("rubric")).put("minItems", 3).put("maxItems", 5);
            }
            variants.add(item);
        }
        if (variants.size() == 1) {
            questions.set("items", variants.get(0));
        } else {
            questions.putObject("items").set("anyOf", variants);
        }
        return root;
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
