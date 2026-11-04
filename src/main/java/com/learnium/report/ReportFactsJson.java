package com.learnium.report;

import com.learnium.api.dto.ReportDtos.StoredReportFacts;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Serializes the frozen facts stored on a report snapshot and reads them back verbatim. */
@Component
public class ReportFactsJson {

    private final ObjectMapper json;

    public ReportFactsJson() {
        this(new ObjectMapper());
    }

    ReportFactsJson(ObjectMapper json) {
        this.json = json;
    }

    String write(StoredReportFacts facts) {
        try {
            return json.writeValueAsString(facts);
        } catch (JacksonException e) {
            throw new IllegalStateException("Report facts could not be serialized", e);
        }
    }

    StoredReportFacts read(String stored) {
        try {
            return json.readValue(stored, StoredReportFacts.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Stored report facts could not be read", e);
        }
    }
}
