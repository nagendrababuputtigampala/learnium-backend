package com.learnium.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GenerationSchemaTest {

    @Test
    void mcqRequestBoundsQuestionsAndOptionsAndRequiresTheFullPayload() {
        JsonNode schema = GenerationSchema.schema(
                GenerationRequests.request(Map.of(GeneratedQuestionType.MCQ, 2)));
        JsonNode questions = schema.path("properties").path("questions");
        JsonNode item = questions.path("items");

        assertThat(questions.path("minItems").asInt()).isEqualTo(2);
        assertThat(questions.path("maxItems").asInt()).isEqualTo(2);
        assertThat(item.path("required").toString())
                .isEqualTo("[\"type\",\"stem\",\"options\",\"correctIndex\"]");
        assertThat(item.path("propertyOrdering")).isEqualTo(item.path("required"));
        JsonNode fields = item.path("properties");
        assertThat(fields.size()).isEqualTo(4);
        assertThat(fields.path("type").path("enum").toString()).isEqualTo("[\"MCQ\"]");
        assertThat(fields.path("options").path("minItems").asInt()).isEqualTo(4);
        assertThat(fields.path("options").path("maxItems").asInt()).isEqualTo(4);
        assertThat(fields.path("correctIndex").path("minimum").asInt()).isZero();
        assertThat(fields.path("correctIndex").path("maximum").asInt()).isEqualTo(3);
    }

    @Test
    void mixedRequestIncludesOnlyRequestedTypesWithRequiredFieldsAndBoundedRubric() {
        JsonNode questions = GenerationSchema.schema(GenerationRequests.request(Map.of(
                        GeneratedQuestionType.FILL_BLANK, 1,
                        GeneratedQuestionType.FLASHCARD, 1,
                        GeneratedQuestionType.NUMERIC, 1,
                        GeneratedQuestionType.WRITTEN, 2,
                        GeneratedQuestionType.MCQ, 0)))
                .path("properties").path("questions");
        assertThat(questions.path("maxItems").asInt()).isEqualTo(5);
        JsonNode variants = questions.path("items").path("anyOf");
        assertThat(variants).hasSize(4);
        for (JsonNode variant : variants) {
            assertThat(variant.path("properties").size()).isEqualTo(variant.path("required").size());
            assertThat(variant.path("properties").path("type").path("enum").toString())
                    .doesNotContain("MCQ");
        }
        JsonNode rubric = variants.get(3).path("properties").path("rubric");
        assertThat(rubric.path("minItems").asInt()).isEqualTo(3);
        assertThat(rubric.path("maxItems").asInt()).isEqualTo(5);
    }

    @Test
    void requestSchemasDoNotMutateTheSharedSchemaOrOtherRequests() {
        String original = GenerationSchema.schema().toString();
        JsonNode mcq = GenerationSchema.schema(GenerationRequests.oneMcq());
        GenerationSchema.schema(GenerationRequests.request(Map.of(GeneratedQuestionType.WRITTEN, 3)));

        assertThat(GenerationSchema.schema().toString()).isEqualTo(original);
        assertThat(mcq.path("properties").path("questions").path("maxItems").asInt()).isEqualTo(1);
        assertThat(mcq.path("properties").path("questions").path("items")
                .path("properties").has("rubric")).isFalse();
    }
}
