package com.learnium.generation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The active model follows the configured provider. */
class GenerationPropertiesTest {

    @Test
    void activeModelDefaultsToTheGeminiModel() {
        GenerationProperties properties = new GenerationProperties();

        assertThat(properties.getProvider()).isEqualTo("gemini");
        assertThat(properties.activeModel()).isEqualTo("gemini-2.0-flash");
    }

    @Test
    void activeModelFollowsTheOllamaProvider() {
        GenerationProperties properties = new GenerationProperties();
        properties.setProvider("ollama");
        properties.getOllama().setModel("qwen2.5");

        assertThat(properties.getProvider()).isEqualTo("ollama");
        assertThat(properties.activeModel()).isEqualTo("qwen2.5");
    }
}
