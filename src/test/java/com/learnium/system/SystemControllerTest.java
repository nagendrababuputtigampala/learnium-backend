package com.learnium.system;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.learnium.common.logging.TraceIdFilter;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pingReturnsUpPayload() throws Exception {
        mockMvc.perform(get("/api/v1/system/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("learnium-backend"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void pingResponseCarriesGeneratedRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/system/ping"))
                .andExpect(status().isOk())
                .andExpect(header().exists(TraceIdFilter.REQUEST_ID_HEADER));
    }

    @Test
    void pingEchoesSuppliedRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/system/ping").header(TraceIdFilter.REQUEST_ID_HEADER, "my-trace-42"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdFilter.REQUEST_ID_HEADER, "my-trace-42"));
    }

    @Test
    void unknownPathRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist")).andExpect(status().isUnauthorized());
    }

    @Test
    void validationFailureReturnsStandardErrorBody() throws Exception {
        mockMvc.perform(post("/test-support/validated")
                        .with(user("tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-VAL-001"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name").value("must not be blank"))
                .andExpect(jsonPath("$.traceId").value(Matchers.not(Matchers.isEmptyOrNullString())))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void malformedJsonReturns400WithVal002() throws Exception {
        mockMvc.perform(post("/test-support/validated")
                        .with(user("tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-VAL-002"))
                .andExpect(jsonPath("$.message").value("Malformed request body"))
                .andExpect(jsonPath("$.traceId").value(Matchers.not(Matchers.isEmptyOrNullString())))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void wrongMethodReturns405WithSys405() throws Exception {
        mockMvc.perform(get("/test-support/validated").with(user("tester")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", Matchers.containsString("POST")))
                .andExpect(jsonPath("$.code").value("LRN-SYS-405"))
                .andExpect(jsonPath("$.message").value("Method not allowed"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void wrongContentTypeReturns415WithSys415() throws Exception {
        mockMvc.perform(post("/test-support/validated")
                        .with(user("tester"))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("LRN-SYS-415"))
                .andExpect(jsonPath("$.message").value("Unsupported media type"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void businessExceptionReturnsStandardErrorBody() throws Exception {
        mockMvc.perform(get("/test-support/business").with(user("tester")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-003"))
                .andExpect(jsonPath("$.message").value("Custom forbidden message"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void unexpectedExceptionReturnsGenericStandardErrorBody() throws Exception {
        mockMvc.perform(get("/test-support/boom").with(user("tester")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(Matchers.not(Matchers.containsString("sensitive internal detail"))))
                .andExpect(jsonPath("$.code").value("LRN-SYS-001"))
                .andExpect(jsonPath("$.message").value("Unexpected server error"));
    }
}
