package com.learnium.marking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.learnium.generation.*;
import com.learnium.model.content.GenerationFailureKind;
import java.util.*;
import org.junit.jupiter.api.Test;

class MarkingServiceTest {
    static final ObjectMapper JSON = new ObjectMapper();
    final ModelTransport transport = mock(ModelTransport.class);
    final GenerationProperties generation = new GenerationProperties();
    final MarkingService service = new MarkingService(transport, generation);
    JsonNode input() {
        var in = JSON.createObjectNode(); var payload = in.putObject("payload"); payload.put("modelAnswer", "Two equal parts");
        var rubric = payload.putArray("rubric");
        for (int i = 1; i <= 3; i++) rubric.addObject().put("id", "crit-" + i).put("criterion", "C" + i).put("maxScore", 1);
        in.put("answer", "Two equal parts."); return in;
    }
    ObjectNode output() {
        var out = JSON.createObjectNode().put("feedback", "Good explanation"); var criteria = out.putArray("criteria");
        for (int i = 1; i <= 3; i++) criteria.addObject().put("criterionId", "crit-" + i).put("score", 0.75).put("feedback", "Good");
        return out;
    }
    MarkingService.Outcome run(String provider) { return service.mark(provider, "frozen-model", MarkingService.PROMPT_VERSION, input()); }
    // Build the wire envelopes explicitly to exercise real provider response parsing.
    String ollamaEnvelope(String content) { var root = JSON.createObjectNode(); root.putObject("message").put("content", content); return root.toString(); }
    String geminiEnvelope(JsonNode output, String finish) {
        var root = JSON.createObjectNode(); var candidate = root.putArray("candidates").addObject(); candidate.put("finishReason", finish);
        var parts = candidate.putObject("content").putArray("parts"); parts.addObject().put("thought", true); parts.addObject().put("text", output.toString());
        return root.toString();
    }
    @Test void providersUseFrozenModelsStructuredOutputAndVersionedPrompt() throws Exception {
        doReturn(new ModelTransport.ModelResponse(200, ollamaEnvelope(output().toString()))).when(transport).send(any());
        var marked = run("ollama"); assertThat(marked.marked().score()).isEqualByComparingTo("2.25");
        assertThat(marked.marked().breakdown().path("criteria").has("crit-1")).isTrue();
        var captor = org.mockito.ArgumentCaptor.forClass(ModelTransport.ModelRequest.class); verify(transport).send(captor.capture());
        var wire = JSON.readTree(captor.getValue().jsonBody());
        assertThat(wire.path("model").asText()).isEqualTo("frozen-model");
        assertThat(wire.path("messages").get(0).path("content").asText()).contains("marking-prompt-v1", "untrusted");
        assertThat(wire.path("format").path("properties").has("criteria")).isTrue();
        generation.getOllama().setBaseUrl("http://localhost:11434/"); run("ollama");
        generation.getGemini().setApiKey("test-key");
        doReturn(new ModelTransport.ModelResponse(200, geminiEnvelope(output(), "STOP"))).when(transport).send(any());
        assertThat(run("gemini").marked().score()).isEqualByComparingTo("2.25");
    }
    @Test void configurationAndProviderFailuresAreClassifiedWithoutContent() {
        assertThat(run("gemini").retryable()).isFalse();
        assertThat(service.mark("ollama", "m", "old-prompt", input()).failure()).isEqualTo(GenerationFailureKind.MODEL_ERROR);
        for (int code : List.of(-1, 408, 429, 500, 503, 400, 401)) {
            doThrow(new ModelTransportException(code)).when(transport).send(any());
            assertThat(run("ollama").retryable()).isEqualTo(code == -1 || code == 408 || code == 429 || code >= 500);
        }
        for (String bad : List.of("broken", "null", "{}", "{\"message\":{\"content\":4}}", ollamaEnvelope("broken"))) {
            doReturn(new ModelTransport.ModelResponse(200, bad)).when(transport).send(any());
            assertThat(run("ollama").failure()).isEqualTo(GenerationFailureKind.MALFORMED_RESPONSE);
        }
        doReturn(new ModelTransport.ModelResponse(200, "{\"done_reason\":\"length\"}")).when(transport).send(any());
        assertThat(run("ollama").failure()).isEqualTo(GenerationFailureKind.TRUNCATED);
        generation.getGemini().setApiKey("test-key");
        doReturn(new ModelTransport.ModelResponse(200, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}")).when(transport).send(any());
        assertThat(run("gemini").failure()).isEqualTo(GenerationFailureKind.SAFETY_REFUSAL);
        for (String finish : List.of("MAX_TOKENS", "SAFETY", "RECITATION", "OTHER")) {
            doReturn(new ModelTransport.ModelResponse(200, geminiEnvelope(output(), finish))).when(transport).send(any());
            assertThat(run("gemini").failure()).isEqualTo(finish.equals("MAX_TOKENS") ? GenerationFailureKind.TRUNCATED : finish.equals("OTHER") ? GenerationFailureKind.MALFORMED_RESPONSE : GenerationFailureKind.SAFETY_REFUSAL);
        }
        doReturn(new ModelTransport.ModelResponse(200, "{}")).when(transport).send(any());
        assertThat(run("gemini").failure()).isEqualTo(GenerationFailureKind.MALFORMED_RESPONSE);
        doReturn(new ModelTransport.ModelResponse(200, geminiEnvelope(output(), ""))).when(transport).send(any());
        assertThat(run("gemini").marked()).isNotNull();
    }
    @Test void rejectsEveryMalformedCriterionWithoutReturningPartialMarks() {
        List<JsonNode> bad = new ArrayList<>(Arrays.asList(null, JSON.nullNode(), JSON.createArrayNode(), JSON.createObjectNode()));
        var noCriteria = output(); noCriteria.put("criteria", "bad"); bad.add(noCriteria);
        var shortCriteria = output(); ((ArrayNode) shortCriteria.path("criteria")).remove(0); bad.add(shortCriteria);
        var noFeedback = output(); noFeedback.remove("feedback"); bad.add(noFeedback);
        bad.add(output().put("feedback", "x".repeat(4001)));
        for (String id : List.of("unknown", "crit-2")) {
            var out = output(); ((ObjectNode) out.path("criteria").get(0)).put("criterionId", id); bad.add(out);
        }
        for (JsonNode score : List.of(JSON.getNodeFactory().textNode("one"), JSON.getNodeFactory().numberNode(Double.POSITIVE_INFINITY),
                JSON.getNodeFactory().numberNode(-1), JSON.getNodeFactory().numberNode(2), JSON.getNodeFactory().numberNode(0.001))) {
            var out = output(); ((ObjectNode) out.path("criteria").get(0)).set("score", score); bad.add(out);
        }
        var missingFeedback = output(); ((ObjectNode) missingFeedback.path("criteria").get(0)).remove("feedback"); bad.add(missingFeedback);
        var longFeedback = output(); ((ObjectNode) longFeedback.path("criteria").get(0)).put("feedback", "x".repeat(4001)); bad.add(longFeedback);
        for (var out : bad) {
            var verdict = service.validate(input().path("payload"), out);
            assertThat(verdict.marked()).isNull(); assertThat(verdict.failure()).isEqualTo(GenerationFailureKind.MALFORMED_RESPONSE); assertThat(verdict.retryable()).isFalse();
        }
    }
    @Test void markingSettingsDefaultIndependentlyToGenerationAndValidateOverrides() {
        var properties = new MarkingProperties();
        assertThat(properties.resolve(generation).provider()).isEqualTo(generation.getProvider());
        assertThat(properties.resolve(generation).model()).isEqualTo(generation.activeModel());
        properties.setProvider("ollama"); properties.setModel("marking-model");
        assertThat(properties.resolve(generation)).isEqualTo(new MarkingProperties.Settings("ollama", "marking-model"));
        properties.setProvider("wrong"); assertThatIllegalStateException().isThrownBy(() -> properties.resolve(generation));
        properties.setProvider("gemini"); properties.setModel("x".repeat(65)); assertThatIllegalStateException().isThrownBy(() -> properties.resolve(generation));
        generation.getGemini().setModel(""); properties.setModel(""); assertThatIllegalStateException().isThrownBy(() -> properties.resolve(generation));
    }
}
