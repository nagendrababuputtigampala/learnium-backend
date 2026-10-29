package com.learnium.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.learnium.generation.GenerationOutcome.Failure;
import com.learnium.generation.GenerationOutcome.Success;
import com.learnium.model.content.GenerationFailureKind;
import org.junit.jupiter.api.Test;

/**
 * Gemini adapter, tested offline against recorded response shapes: the
 * envelope is assembled with the same field layout the real API returns.
 */
class GeminiQuestionGeneratorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MODEL_JSON =
            "{\"questions\":[{\"type\":\"MCQ\",\"stem\":\"What is 1/2 + 1/4?\","
                    + "\"options\":[{\"text\":\"3/4\"},{\"text\":\"1/2\"},{\"text\":\"2/6\"},"
                    + "{\"text\":\"1/6\"}],\"correctIndex\":0}]}";

    private final StubModelTransport transport = new StubModelTransport();

    private GeminiQuestionGenerator generator() {
        GenerationProperties properties = new GenerationProperties();
        properties.getGemini().setApiKey("test-key");
        properties.getGemini().setModel("gemini-2.0-flash");
        return new GeminiQuestionGenerator(properties, transport);
    }

    /** Builds a generateContent response envelope around a model JSON payload. */
    private static String envelope(String modelJson, String finishReason, boolean withUsage)
            throws Exception {
        ObjectNode root = JSON.createObjectNode();
        ObjectNode candidate = root.putArray("candidates").addObject();
        ObjectNode content = candidate.putObject("content");
        content.put("role", "model");
        content.putArray("parts").addObject().put("text", modelJson);
        if (finishReason != null) {
            candidate.put("finishReason", finishReason);
        }
        candidate.put("index", 0);
        if (withUsage) {
            ObjectNode usage = root.putObject("usageMetadata");
            usage.put("promptTokenCount", 1200);
            usage.put("candidatesTokenCount", 300);
            usage.put("thoughtsTokenCount", 42);
            usage.put("totalTokenCount", 1542);
        }
        return JSON.writeValueAsString(root);
    }

    @Test
    void successParsesQuestionsAndUsage() throws Exception {
        transport.responding(envelope(MODEL_JSON, "STOP", true));

        GenerationOutcome outcome = generator().generate(GenerationRequests.oneMcq());

        assertThat(outcome).isInstanceOf(Success.class);
        Success success = (Success) outcome;
        assertThat(success.questions()).hasSize(1);
        assertThat(success.questions().get(0).get("type").asText()).isEqualTo("MCQ");
        assertThat(success.usageInputTokens()).isEqualTo(1200L);
        assertThat(success.usageOutputTokens()).isEqualTo(300L);
        assertThat(success.usageThinkingTokens()).isEqualTo(42L);
        assertThat(success.providerRequestId()).isNull();
    }

    @Test
    void successWithoutUsageMetadataLeavesUsageNull() throws Exception {
        transport.responding(envelope(MODEL_JSON, "STOP", false));

        Success success = (Success) generator().generate(GenerationRequests.oneMcq());

        assertThat(success.usageInputTokens()).isNull();
        assertThat(success.usageOutputTokens()).isNull();
        assertThat(success.usageThinkingTokens()).isNull();
    }

    @Test
    void requestUsesStructuredOutputAndTheConfiguredModel() throws Exception {
        transport.responding(envelope(MODEL_JSON, "STOP", true));
        generator().generate(GenerationRequests.oneMcq());

        ModelTransport.ModelRequest sent = transport.captured();
        assertThat(sent.url())
                .isEqualTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/"
                                + "gemini-2.0-flash:generateContent");
        assertThat(sent.headers()).containsEntry("x-goog-api-key", "test-key");
        assertThat(sent.timeoutSeconds()).isEqualTo(120L);
        JsonNode body = JSON.readTree(sent.jsonBody());
        JsonNode config = body.get("generationConfig");
        assertThat(config.get("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(config.has("responseSchema")).isTrue();
        assertThat(config.get("temperature").asDouble()).isEqualTo(0.4);
        assertThat(config.get("maxOutputTokens").asInt()).isEqualTo(8192);
        assertThat(body.get("systemInstruction").get("parts").get(0).get("text").asText())
                .contains("JSON only");
        assertThat(body.get("contents").get(0).get("parts").get(0).get("text").asText())
                .contains("Fractions represent parts of a whole");
    }

    @Test
    void promptFeedbackBlockIsASafetyRefusal() {
        transport.responding("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");

        Failure failure = (Failure) generator().generate(GenerationRequests.oneMcq());

        assertThat(failure.kind()).isEqualTo(GenerationFailureKind.SAFETY_REFUSAL);
    }

    @Test
    void safetyAndRecitationFinishesAreSafetyRefusals() throws Exception {
        transport.responding(envelope(MODEL_JSON, "SAFETY", false));
        assertThat(((Failure) generator().generate(GenerationRequests.oneMcq())).kind())
                .isEqualTo(GenerationFailureKind.SAFETY_REFUSAL);
        transport.responding(envelope(MODEL_JSON, "RECITATION", false));
        assertThat(((Failure) generator().generate(GenerationRequests.oneMcq())).kind())
                .isEqualTo(GenerationFailureKind.SAFETY_REFUSAL);
    }

    @Test
    void maxTokensFinishIsTruncated() throws Exception {
        transport.responding(envelope("{\"questions\":[{\"type\":\"MCQ\"", "MAX_TOKENS", false));

        Failure failure = (Failure) generator().generate(GenerationRequests.oneMcq());

        assertThat(failure.kind()).isEqualTo(GenerationFailureKind.TRUNCATED);
    }

    @Test
    void unknownFinishReasonIsMalformed() throws Exception {
        transport.responding(envelope(MODEL_JSON, "OTHER", false));

        Failure failure = (Failure) generator().generate(GenerationRequests.oneMcq());

        assertThat(failure.kind()).isEqualTo(GenerationFailureKind.MALFORMED_RESPONSE);
    }

    @Test
    void missingOrEmptyCandidatesAreMalformed() {
        transport.responding("{\"usageMetadata\":{}}");
        assertThat(((Failure) generator().generate(GenerationRequests.oneMcq())).kind())
                .isEqualTo(GenerationFailureKind.MALFORMED_RESPONSE);
        transport.responding("{\"candidates\":[]}");
        assertThat(((Failure) generator().generate(GenerationRequests.oneMcq())).kind())
                .isEqualTo(GenerationFailureKind.MALFORMED_RESPONSE);
    }

    @Test
    void candidateWithoutContentPartsIsMalformed() throws Exception {
        ObjectNode root = JSON.createObjectNode();
        root.putArray("candidates").addObject().put("finishReason", "STOP");
        transport.responding(JSON.writeValueAsString(root));

        Failure failure = (Failure) generator().generate(GenerationRequests.oneMcq());

        assertThat(failure.kind()).isEqualTo(GenerationFailureKind.MALFORMED_RESPONSE);
    }

    @Test
    void unparseableEnvelopeAndContentAreMalformed() throws Exception {
        transport.responding("this is not json");
        Failure envelopeFailure = (Failure) generator().generate(GenerationRequests.oneMcq());
        assertThat(envelopeFailure.kind()).isEqualTo(GenerationFailureKind.MALFORMED_RESPONSE);
        assertThat(envelopeFailure.errorClass()).isEqualTo("JsonParseException");

        transport.responding(envelope("not json either", "STOP", false));
        Failure contentFailure = (Failure) generator().generate(GenerationRequests.oneMcq());
        assertThat(contentFailure.kind()).isEqualTo(GenerationFailureKind.MALFORMED_RESPONSE);
        assertThat(contentFailure.errorClass()).isEqualTo("JsonParseException");
    }

    @Test
    void contentWithoutAQuestionsArrayIsMalformed() throws Exception {
        transport.responding(envelope("{\"items\":[]}", "STOP", false));

        Failure failure = (Failure) generator().generate(GenerationRequests.oneMcq());

        assertThat(failure.kind()).isEqualTo(GenerationFailureKind.MALFORMED_RESPONSE);
    }

    @Test
    void transportFailureIsAModelError() {
        transport.failing(new ModelTransportException(503));

        Failure failure = (Failure) generator().generate(GenerationRequests.oneMcq());

        assertThat(failure.kind()).isEqualTo(GenerationFailureKind.MODEL_ERROR);
        assertThat(failure.errorClass()).isEqualTo("ModelTransportException");
    }

    @Test
    void missingApiKeyIsAModelErrorAndNeverCallsOut() {
        GenerationProperties properties = new GenerationProperties();
        properties.getGemini().setApiKey("");
        GeminiQuestionGenerator generator = new GeminiQuestionGenerator(properties, transport);

        Failure failure = (Failure) generator.generate(GenerationRequests.oneMcq());

        assertThat(failure.kind()).isEqualTo(GenerationFailureKind.MODEL_ERROR);
        assertThat(failure.errorClass()).isEqualTo("IllegalStateException");
        assertThat(transport.captured()).isNull();
    }
}
