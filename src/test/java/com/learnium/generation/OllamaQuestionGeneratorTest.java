package com.learnium.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.learnium.generation.GenerationOutcome.Failure;
import com.learnium.generation.GenerationOutcome.Success;
import com.learnium.model.content.GenerationFailureKind;
import org.junit.jupiter.api.Test;

/** Ollama adapter, tested offline against recorded /api/chat response shapes. */
class OllamaQuestionGeneratorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MODEL_JSON =
            "{\"questions\":[{\"type\":\"FLASHCARD\",\"front\":\"One half\","
                    + "\"back\":\"Two quarters\"}]}";

    private final StubModelTransport transport = new StubModelTransport();

    private OllamaQuestionGenerator generator() {
        return new OllamaQuestionGenerator(new GenerationProperties(), transport);
    }

    private static String envelope(String content, String doneReason) throws Exception {
        ObjectNode root = JSON.createObjectNode();
        root.putObject("message").put("role", "assistant").put("content", content);
        root.put("done_reason", doneReason);
        root.put("prompt_eval_count", 900);
        root.put("eval_count", 250);
        return JSON.writeValueAsString(root);
    }

    @Test
    void successParsesQuestionsAndEvalCounts() throws Exception {
        transport.responding(envelope(MODEL_JSON, "stop"));

        GenerationOutcome outcome = generator().generate(GenerationRequests.oneMcq());

        assertThat(outcome).isInstanceOf(Success.class);
        Success success = (Success) outcome;
        assertThat(success.questions()).hasSize(1);
        assertThat(success.usageInputTokens()).isEqualTo(900L);
        assertThat(success.usageOutputTokens()).isEqualTo(250L);
        assertThat(success.usageThinkingTokens()).isNull();
    }

    @Test
    void requestPostsTheSchemaAndMessagesToTheChatEndpoint() throws Exception {
        transport.responding(envelope(MODEL_JSON, "stop"));
        generator().generate(GenerationRequests.oneMcq());

        ModelTransport.ModelRequest sent = transport.captured();
        assertThat(sent.url()).isEqualTo("http://localhost:11434/api/chat");
        JsonNode body = JSON.readTree(sent.jsonBody());
        assertThat(body.get("model").asText()).isEqualTo("llama3.1");
        assertThat(body.get("stream").asBoolean()).isFalse();
        assertThat(body.has("format")).isTrue();
        assertThat(body.get("messages").get(0).get("role").asText()).isEqualTo("system");
        assertThat(body.get("messages").get(1).get("content").asText())
                .contains("Fractions represent parts of a whole");
    }

    @Test
    void baseUrlTrailingSlashIsTrimmed() throws Exception {
        GenerationProperties properties = new GenerationProperties();
        properties.getOllama().setBaseUrl("http://localhost:9999/");
        transport.responding(envelope(MODEL_JSON, "stop"));

        new OllamaQuestionGenerator(properties, transport).generate(GenerationRequests.oneMcq());

        assertThat(transport.captured().url()).isEqualTo("http://localhost:9999/api/chat");
    }

    @Test
    void questionsThatAreNotAnArrayAreMalformed() throws Exception {
        transport.responding(envelope("{\"questions\":{}}", "stop"));

        assertThat(((Failure) generator().generate(GenerationRequests.oneMcq())).kind())
                .isEqualTo(GenerationFailureKind.MALFORMED_RESPONSE);
    }

    @Test
    void lengthDoneReasonIsTruncated() throws Exception {
        transport.responding(envelope("{\"questions\":[", "length"));

        Failure failure = (Failure) generator().generate(GenerationRequests.oneMcq());

        assertThat(failure.kind()).isEqualTo(GenerationFailureKind.TRUNCATED);
    }

    @Test
    void malformedEnvelopeContentAndShapeAreMalformed() throws Exception {
        transport.responding("not json");
        assertThat(((Failure) generator().generate(GenerationRequests.oneMcq())).kind())
                .isEqualTo(GenerationFailureKind.MALFORMED_RESPONSE);

        transport.responding("{}");
        assertThat(((Failure) generator().generate(GenerationRequests.oneMcq())).kind())
                .isEqualTo(GenerationFailureKind.MALFORMED_RESPONSE);

        transport.responding(envelope("not json", "stop"));
        Failure contentFailure = (Failure) generator().generate(GenerationRequests.oneMcq());
        assertThat(contentFailure.kind()).isEqualTo(GenerationFailureKind.MALFORMED_RESPONSE);
        assertThat(contentFailure.errorClass()).isEqualTo("JsonParseException");

        transport.responding(envelope("{\"items\":[]}", "stop"));
        assertThat(((Failure) generator().generate(GenerationRequests.oneMcq())).kind())
                .isEqualTo(GenerationFailureKind.MALFORMED_RESPONSE);
    }

    @Test
    void transportFailureIsAModelError() {
        transport.failing(new ModelTransportException(-1, new java.io.IOException("down")));

        Failure failure = (Failure) generator().generate(GenerationRequests.oneMcq());

        assertThat(failure.kind()).isEqualTo(GenerationFailureKind.MODEL_ERROR);
        assertThat(failure.errorClass()).isEqualTo("ModelTransportException");
    }
}
