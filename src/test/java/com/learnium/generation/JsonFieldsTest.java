package com.learnium.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Every arm of the null-safe JSON readers the provider adapters share. */
class JsonFieldsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode node(String json) throws Exception {
        return JSON.readTree(json);
    }

    @Test
    void textOrNullCoversMissingNullWrongTypeAndText() throws Exception {
        assertThat(JsonFields.textOrNull(null, "a")).isNull();
        assertThat(JsonFields.textOrNull(node("{}"), "a")).isNull();
        assertThat(JsonFields.textOrNull(node("{\"a\":null}"), "a")).isNull();
        assertThat(JsonFields.textOrNull(node("{\"a\":5}"), "a")).isNull();
        assertThat(JsonFields.textOrNull(node("{\"a\":\"x\"}"), "a")).isEqualTo("x");
    }

    @Test
    void longOrNullCoversMissingNullWrongTypeAndNumber() throws Exception {
        assertThat(JsonFields.longOrNull(null, "a")).isNull();
        assertThat(JsonFields.longOrNull(node("{}"), "a")).isNull();
        assertThat(JsonFields.longOrNull(node("{\"a\":null}"), "a")).isNull();
        assertThat(JsonFields.longOrNull(node("{\"a\":\"5\"}"), "a")).isNull();
        assertThat(JsonFields.longOrNull(node("{\"a\":5}"), "a")).isEqualTo(5L);
    }

    @Test
    void childOrNullCoversMissingNullAndPresent() throws Exception {
        assertThat(JsonFields.childOrNull(null, "a")).isNull();
        assertThat(JsonFields.childOrNull(node("{}"), "a")).isNull();
        assertThat(JsonFields.childOrNull(node("{\"a\":null}"), "a")).isNull();
        assertThat(JsonFields.childOrNull(node("{\"a\":{\"b\":1}}"), "a"))
                .isEqualTo(node("{\"b\":1}"));
    }
}
