package com.learnium.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnium.generation.GenerationOutcome.Failure;
import org.junit.jupiter.api.Test;

class RoutingQuestionGeneratorTest {
    private final GenerationProperties properties = new GenerationProperties();
    private final StubModelTransport transport = new StubModelTransport();
    private final RoutingQuestionGenerator generator = new RoutingQuestionGenerator(properties, transport);

    private GenerationRequest frozen(String provider, String model) {
        GenerationRequest base = GenerationRequests.oneMcq();
        return new GenerationRequest(base.sourceText(), base.topicName(), base.gradeName(),
                base.subjectName(), base.counts(), base.difficulty(), base.payloadSchemaVersion(),
                provider, model);
    }

    @Test
    void geminiJobKeepsItsProviderAndModelAfterConfigurationChanges() {
        properties.setProvider("ollama");
        properties.getGemini().setApiKey("test-key");
        properties.getGemini().setModel("new-gemini-model");
        generator.generate(frozen("gemini", "frozen-gemini-model"));

        assertThat(transport.captured().url()).endsWith("/frozen-gemini-model:generateContent");
        assertThat(properties.getProvider()).isEqualTo("ollama");
        assertThat(properties.getGemini().getModel()).isEqualTo("new-gemini-model");
    }

    @Test
    void ollamaJobKeepsItsProviderAndModelAfterConfigurationChanges() throws Exception {
        properties.setProvider("gemini");
        properties.getOllama().setModel("new-ollama-model");
        generator.generate(frozen("ollama", "frozen-ollama-model"));

        assertThat(transport.captured().url()).endsWith("/api/chat");
        assertThat(new ObjectMapper().readTree(transport.captured().jsonBody()).path("model").asText())
                .isEqualTo("frozen-ollama-model");
        assertThat(properties.getOllama().getModel()).isEqualTo("new-ollama-model");
    }

    @Test
    void requestsWithoutFrozenInputsUseTheConfiguredProvider() {
        properties.setProvider("ollama");
        generator.generate(GenerationRequests.oneMcq());
        assertThat(transport.captured().url()).endsWith("/api/chat");
    }

    @Test
    void unsupportedFrozenProviderFailsWithoutCallingAnotherProvider() {
        Failure failure = (Failure) generator.generate(frozen("unknown", "model"));
        assertThat(failure.errorClass()).isEqualTo("UnsupportedGenerationProvider");
        assertThat(transport.captured()).isNull();
    }
}
