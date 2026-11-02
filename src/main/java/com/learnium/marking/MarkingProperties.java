package com.learnium.marking;

import com.learnium.generation.GenerationProperties;
import java.util.Set;
import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "learnium.marking")
@Getter @Setter
public class MarkingProperties {
    private String provider = "";
    private String model = "";
    public record Settings(String provider, String model) {}
    public Settings resolve(GenerationProperties generation) {
        String selectedProvider = provider.isBlank() ? generation.getProvider() : provider;
        String selectedModel = model.isBlank() ? generation.activeModel() : model;
        if (!Set.of("gemini", "ollama").contains(selectedProvider) || selectedModel.isBlank() || selectedModel.length() > 64)
            throw new IllegalStateException("Invalid marking provider/model configuration");
        return new Settings(selectedProvider, selectedModel);
    }
}
