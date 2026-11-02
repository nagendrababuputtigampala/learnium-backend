package com.learnium.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.generation.PromptBuilder.Prompt;
import com.learnium.model.content.GenerationDifficulty;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    @Test
    void promptCarriesContextCountsRulesAndSource() {
        EnumMap<GeneratedQuestionType, Integer> counts = new EnumMap<>(GeneratedQuestionType.class);
        counts.put(GeneratedQuestionType.MCQ, 2);
        counts.put(GeneratedQuestionType.WRITTEN, 1);
        Prompt prompt = builder.build(GenerationRequests.request(counts));

        assertThat(prompt.system()).contains("ONLY from the source text", "JSON only");
        assertThat(prompt.user())
                .contains("Grade 8 Mathematics, topic: Fractions")
                .contains("Difficulty: MEDIUM")
                .contains("- 2 multiple-choice questions")
                .contains("- 1 written-answer questions")
                .contains("answerable from the source text alone")
                .contains("Fractions represent parts of a whole");
        // Unrequested types produce no count line.
        assertThat(prompt.user()).doesNotContain("flashcards", "numeric-answer questions");
    }

    @Test
    void openingLineUsesTheGradeNameAsIs() {
        // Grade display names already contain "Grade" ("Grade 6"): the
        // opening line must use the name verbatim, not prefix it again.
        GenerationRequest request =
                new GenerationRequest(
                        "Fractions represent parts of a whole.",
                        "Fractions",
                        "Grade 6",
                        "Mathematics",
                        Map.of(GeneratedQuestionType.MCQ, 1),
                        GenerationDifficulty.MEDIUM,
                        GenerationContract.PAYLOAD_SCHEMA_VERSION);
        Prompt prompt = builder.build(request);

        assertThat(prompt.user())
                .startsWith(
                        "Write practice questions for Grade 6 Mathematics, topic: Fractions.\n");
    }

    @Test
    void promptListsEveryTypeWhenAllRequested() {
        EnumMap<GeneratedQuestionType, Integer> counts = new EnumMap<>(GeneratedQuestionType.class);
        for (GeneratedQuestionType type : GeneratedQuestionType.values()) {
            counts.put(type, 1);
        }
        Prompt prompt = builder.build(GenerationRequests.request(counts));
        assertThat(prompt.user())
                .contains("- 1 multiple-choice questions")
                .contains("- 1 fill-in-the-blank questions")
                .contains("- 1 flashcards")
                .contains("- 1 numeric-answer questions")
                .contains("- 1 written-answer questions")
                .contains("_____")
                .contains("rubric has 3 to 5 criteria");
    }

    @Test
    void requestCopiesCountsAndCountsAbsentTypesAsZero() {
        Map<GeneratedQuestionType, Integer> counts = new HashMap<>();
        counts.put(GeneratedQuestionType.MCQ, 3);
        GenerationRequest request = GenerationRequests.request(counts);
        counts.put(GeneratedQuestionType.MCQ, 99);

        assertThat(request.countFor(GeneratedQuestionType.MCQ)).isEqualTo(3);
        assertThat(request.countFor(GeneratedQuestionType.NUMERIC)).isZero();
        assertThat(request.totalRequested()).isEqualTo(3);
    }

    @Test
    void schemaExpressesTheContract() {
        JsonNode schema = GenerationSchema.schema();
        assertThat(schema.get("type").asText()).isEqualTo("object");
        assertThat(schema.get("required").get(0).asText()).isEqualTo("questions");
        JsonNode item =
                schema.get("properties").get("questions").get("items");
        assertThat(item.get("required").get(0).asText()).isEqualTo("type");
        assertThat(item.get("properties").get("type").get("enum"))
                .extracting(JsonNode::asText)
                .containsExactly("MCQ", "FILL_BLANK", "FLASHCARD", "NUMERIC", "WRITTEN");
        assertThat(item.get("properties").has("rubric")).isTrue();
        assertThat(item.get("properties").has("workedSolution")).isTrue();
    }
}
