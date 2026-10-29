package com.learnium.generation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.learnium.generation.GenerationOutcome.Failure;
import com.learnium.generation.GenerationOutcome.Success;
import com.learnium.generation.ModelTransport.ModelRequest;
import com.learnium.model.content.GenerationFailureKind;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Gemini adapter (production provider). Uses the provider's structured
 * output (JSON mime type + the shared response schema) AND leaves the
 * strict per-type rules to server-side validation. Failure mapping:
 * transport/HTTP problems → MODEL_ERROR; a safety block (prompt
 * feedback or a SAFETY/RECITATION finish) → SAFETY_REFUSAL; a
 * MAX_TOKENS finish → TRUNCATED; an unparseable or off-contract body →
 * MALFORMED_RESPONSE.
 */
@Component
@ConditionalOnProperty(
        name = "learnium.generation.provider",
        havingValue = "gemini",
        matchIfMissing = true)
public class GeminiQuestionGenerator implements QuestionGenerator {

    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final GenerationProperties properties;
    private final ModelTransport transport;
    private final PromptBuilder promptBuilder = new PromptBuilder();

    public GeminiQuestionGenerator(GenerationProperties properties, ModelTransport transport) {
        this.properties = properties;
        this.transport = transport;
    }

    @Override
    public GenerationOutcome generate(GenerationRequest request) {
        GenerationProperties.Gemini config = properties.getGemini();
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            // No key configured: a setup error, surfaced as a model error
            // rather than a crash — the job records it and can be retried
            // once the key exists.
            return new Failure(GenerationFailureKind.MODEL_ERROR, "IllegalStateException");
        }
        String bodyJson = writeJson(buildRequestBody(promptBuilder.build(request)));
        ModelTransport.ModelResponse response;
        try {
            response =
                    transport.send(
                            new ModelRequest(
                                    API_BASE + config.getModel() + ":generateContent",
                                    Map.of(
                                            "x-goog-api-key", config.getApiKey(),
                                            "Content-Type", "application/json"),
                                    bodyJson,
                                    config.getTimeoutSeconds()));
        } catch (ModelTransportException e) {
            return new Failure(GenerationFailureKind.MODEL_ERROR, e.getClass().getSimpleName());
        }
        return parseResponse(response.body());
    }

    private ObjectNode buildRequestBody(PromptBuilder.Prompt prompt) {
        ObjectNode body = JSON.createObjectNode();
        ObjectNode systemInstruction = body.putObject("systemInstruction");
        systemInstruction.putArray("parts").addObject().put("text", prompt.system());
        ObjectNode content = body.putArray("contents").addObject();
        content.put("role", "user");
        content.putArray("parts").addObject().put("text", prompt.user());
        ObjectNode generationConfig = body.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.set("responseSchema", GenerationSchema.schema());
        generationConfig.put("temperature", 0.4);
        generationConfig.put("maxOutputTokens", 8192);
        return body;
    }

    private GenerationOutcome parseResponse(String body) {
        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (JsonProcessingException e) {
            return new Failure(
                    GenerationFailureKind.MALFORMED_RESPONSE, e.getClass().getSimpleName());
        }
        JsonNode promptFeedback = JsonFields.childOrNull(root, "promptFeedback");
        if (JsonFields.textOrNull(promptFeedback, "blockReason") != null) {
            return new Failure(GenerationFailureKind.SAFETY_REFUSAL, null);
        }
        JsonNode candidates = JsonFields.childOrNull(root, "candidates");
        if (candidates == null || !candidates.isArray() || candidates.isEmpty()) {
            return new Failure(GenerationFailureKind.MALFORMED_RESPONSE, null);
        }
        JsonNode candidate = candidates.get(0);
        String finishReason = JsonFields.textOrNull(candidate, "finishReason");
        if ("MAX_TOKENS".equals(finishReason)) {
            return new Failure(GenerationFailureKind.TRUNCATED, null);
        }
        if ("SAFETY".equals(finishReason) || "RECITATION".equals(finishReason)) {
            return new Failure(GenerationFailureKind.SAFETY_REFUSAL, null);
        }
        if (finishReason != null && !"STOP".equals(finishReason)) {
            return new Failure(GenerationFailureKind.MALFORMED_RESPONSE, null);
        }
        JsonNode parts = JsonFields.childOrNull(JsonFields.childOrNull(candidate, "content"), "parts");
        if (parts == null || !parts.isArray()) {
            return new Failure(GenerationFailureKind.MALFORMED_RESPONSE, null);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : parts) {
            String partText = JsonFields.textOrNull(part, "text");
            if (partText != null) {
                text.append(partText);
            }
        }
        JsonNode parsed;
        try {
            parsed = JSON.readTree(text.toString());
        } catch (JsonProcessingException e) {
            return new Failure(
                    GenerationFailureKind.MALFORMED_RESPONSE, e.getClass().getSimpleName());
        }
        JsonNode questions = JsonFields.childOrNull(parsed, "questions");
        if (questions == null || !questions.isArray()) {
            return new Failure(GenerationFailureKind.MALFORMED_RESPONSE, null);
        }
        JsonNode usage = JsonFields.childOrNull(root, "usageMetadata");
        return new Success(
                questions,
                JsonFields.longOrNull(usage, "promptTokenCount"),
                JsonFields.longOrNull(usage, "candidatesTokenCount"),
                JsonFields.longOrNull(usage, "thoughtsTokenCount"),
                null);
    }

    private static String writeJson(JsonNode node) {
        try {
            return JSON.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            // Serializing a tree we built cannot fail; treat as a bug.
            throw new IllegalStateException("Could not serialize the generation request", e);
        }
    }
}
