package com.learnium.generation;

import com.learnium.model.content.GenerationFailureKind;

/** Routes frozen job inputs without mutating configuration shared by concurrent jobs. */
public final class RoutingQuestionGenerator implements QuestionGenerator {
    private final GenerationProperties properties;
    private final QuestionGenerator gemini;
    private final QuestionGenerator ollama;

    public RoutingQuestionGenerator(GenerationProperties properties, ModelTransport transport) {
        this.properties = properties;
        this.gemini = new GeminiQuestionGenerator(properties, transport);
        this.ollama = new OllamaQuestionGenerator(properties, transport);
    }

    @Override
    public GenerationOutcome generate(GenerationRequest request) {
        String provider = request.provider() == null ? properties.getProvider() : request.provider();
        return switch (provider) {
            case "gemini" -> gemini.generate(request);
            case "ollama" -> ollama.generate(request);
            default -> new GenerationOutcome.Failure(
                    GenerationFailureKind.MODEL_ERROR, "UnsupportedGenerationProvider");
        };
    }
}
