package com.learnium.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The requested-counts reader: a job's snapshot always carries every
 * type (missing means zero), and a null snapshot — impossible through
 * the schema, tolerated defensively — reads as all zeros.
 */
class GenerationJobServiceCountsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void nullSnapshotReadsAsAllZeros() {
        Map<String, Integer> counts = GenerationJobService.countsFromJson(null);

        assertThat(counts)
                .containsOnly(
                        Map.entry("MCQ", 0),
                        Map.entry("FILL_BLANK", 0),
                        Map.entry("FLASHCARD", 0),
                        Map.entry("NUMERIC", 0),
                        Map.entry("WRITTEN", 0));
    }

    @Test
    void partialSnapshotFillsTheMissingTypesWithZero() throws Exception {
        Map<String, Integer> counts =
                GenerationJobService.countsFromJson(JSON.readTree("{\"MCQ\":4,\"WRITTEN\":2}"));

        assertThat(counts)
                .containsEntry("MCQ", 4)
                .containsEntry("WRITTEN", 2)
                .containsEntry("FLASHCARD", 0);
    }
}
