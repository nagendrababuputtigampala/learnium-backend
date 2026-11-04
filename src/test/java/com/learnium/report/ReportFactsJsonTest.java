package com.learnium.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.learnium.api.dto.ReportDtos.ReportOverallDto;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.learnium.api.dto.ReportDtos.StoredReportFacts;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Round-trip and defensive failure paths of the snapshot facts serializer. */
class ReportFactsJsonTest {

    private StoredReportFacts sample() {
        return new StoredReportFacts(
                "Simran Kaur",
                "simran.k",
                "Batch 8",
                new ReportOverallDto(3, 1, new BigDecimal("24.00"), new BigDecimal("30"), new BigDecimal("80.00"), new BigDecimal("75.00")),
                List.of(),
                List.of());
    }

    @Test
    void roundTripsFacts() {
        ReportFactsJson codec = new ReportFactsJson();

        StoredReportFacts restored = codec.read(codec.write(sample()));

        assertThat(restored).isEqualTo(sample());
    }

    @Test
    void writeFailureIsAnIllegalState() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(new JacksonException("boom") {});
        ReportFactsJson codec = new ReportFactsJson(mapper);

        assertThatThrownBy(() -> codec.write(sample())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void readFailureIsAnIllegalState() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.readValue(anyString(), eq(StoredReportFacts.class)))
                .thenThrow(new JacksonException("boom") {});
        ReportFactsJson codec = new ReportFactsJson(mapper);

        assertThatThrownBy(() -> codec.read("not json")).isInstanceOf(IllegalStateException.class);
    }
}
