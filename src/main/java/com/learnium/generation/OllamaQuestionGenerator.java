package com.learnium.generation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.learnium.generation.GenerationOutcome.Failure;
import com.learnium.generation.GenerationOutcome.Success;
import com.learnium.generation.ModelTransport.ModelRequest;
import com.learnium.model.content.GenerationFailureKind;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Ollama adapter (local development provider): free and keyless, at the
 * cost of weaker schema discipline — which server-side validation
 * absorbs as a lower yield rather than bad drafts. Same prompt, same
 * response schema, same outcome contract as Gemini; usage comes from
 * the eval counters and there is no thinking-token split.
 */
@Component
@ConditionalOnProperty(name = "learnium.generation.provider", havingValue = "ollama")
public class OllamaQuestionGenerator implements QuestionGenerator {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final GenerationProperties properties;
    private final ModelTransport transport;
    private final PromptBuilder promptBuilder = new PromptBuilder();

    public OllamaQuestionGenerator(GenerationProperties properties, ModelTransport transport) {
        this.properties = properties;
        this.transport = transport;
    }

    @Override
    public GenerationOutcome generate(GenerationRequest request) {
        GenerationProperties.Ollama config = properties.getOllama();
        String bodyJson = writeJson(buildRequestBody(promptBuilder.build(request), config));
        ModelTransport.ModelResponse response;
        try {
            response =
                    transport.send(
                            new ModelRequest(
                                    chatUrl(config),
                                    Map.of("Content-Type", "application/json"),
                                    bodyJson,
                                    config.getTimeoutSeconds()));
        } catch (ModelTransportException e) {
            return new Failure(GenerationFailureKind.MODEL_ERROR, e.getClass().getSimpleName());
        }
        return parseResponse(response.body());
    }

    private static String chatUrl(GenerationProperties.Ollama config) {
        String base = config.getBaseUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/api/chat";
    }

    private ObjectNode buildRequestBody(
            PromptBuilder.Prompt prompt, GenerationProperties.Ollama config) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", config.getModel());
        body.put("stream", false);
        body.set("format", GenerationSchema.schema());
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", prompt.system());
        messages.addObject().put("role", "user").put("content", prompt.user());
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
        if ("length".equals(JsonFields.textOrNull(root, "done_reason"))) {
            return new Failure(GenerationFailureKind.TRUNCATED, null);
        }
        String content =
                JsonFields.textOrNull(JsonFields.childOrNull(root, "message"), "content");
        if (content == null) {
            return new Failure(GenerationFailureKind.MALFORMED_RESPONSE, null);
        }
        JsonNode parsed;
        try {
            parsed = JSON.readTree(content);
        } catch (JsonProcessingException e) {
            return new Failure(
                    GenerationFailureKind.MALFORMED_RESPONSE, e.getClass().getSimpleName());
        }
        JsonNode questions = JsonFields.childOrNull(parsed, "questions");
        if (questions == null || !questions.isArray()) {
            return new Failure(GenerationFailureKind.MALFORMED_RESPONSE, null);
        }
        return new Success(
                questions,
                JsonFields.longOrNull(root, "prompt_eval_count"),
                JsonFields.longOrNull(root, "eval_count"),
                null,
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
