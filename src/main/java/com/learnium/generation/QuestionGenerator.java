package com.learnium.generation;

/**
 * Turns source text into raw question items. Implementations are the
 * provider adapters (Gemini for production, Ollama for local
 * development); tests substitute fakes. Implementations classify their
 * own failures into {@link GenerationOutcome.Failure} and never throw
 * for provider problems.
 */
public interface QuestionGenerator {

    GenerationOutcome generate(GenerationRequest request);
}
