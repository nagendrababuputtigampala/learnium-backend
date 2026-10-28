package com.learnium.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void filterRunsAtHighestPrecedenceSoItPrecedesTheSecurityChain() {
        // Security rejections (401/403) must still be traced: this filter has to
        // be ordered before the springSecurityFilterChain registration.
        assertThat(OrderUtils.getOrder(TraceIdFilter.class))
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    void incomingRequestIdIsReusedAndEchoed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.REQUEST_ID_HEADER, "caller-trace-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInsideChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> traceIdInsideChain.set(MDC.get(TraceIdFilter.MDC_TRACE_ID));

        filter.doFilter(request, response, chain);

        assertThat(traceIdInsideChain.get()).isEqualTo("caller-trace-1");
        assertThat(response.getHeader(TraceIdFilter.REQUEST_ID_HEADER)).isEqualTo("caller-trace-1");
        assertThat(MDC.get(TraceIdFilter.MDC_TRACE_ID)).isNull();
    }

    @Test
    void missingRequestIdGeneratesUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInsideChain = new AtomicReference<>();
        AtomicReference<String> requestIdInsideChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            traceIdInsideChain.set(MDC.get(TraceIdFilter.MDC_TRACE_ID));
            requestIdInsideChain.set(MDC.get(TraceIdFilter.MDC_REQUEST_ID));
        };

        filter.doFilter(request, response, chain);

        assertThat(traceIdInsideChain.get()).isNotBlank();
        assertThat(requestIdInsideChain.get()).isEqualTo(traceIdInsideChain.get());
        assertThat(response.getHeader(TraceIdFilter.REQUEST_ID_HEADER))
                .isEqualTo(traceIdInsideChain.get());
        assertThat(MDC.get(TraceIdFilter.MDC_TRACE_ID)).isNull();
    }

    @Test
    void blankRequestIdGeneratesUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.REQUEST_ID_HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        filter.doFilter(request, response, chain);

        String echoed = response.getHeader(TraceIdFilter.REQUEST_ID_HEADER);
        assertThat(echoed).isNotBlank();
        assertThat(echoed).isNotEqualTo("   ");
    }

    @Test
    void mdcIsClearedEvenWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get(TraceIdFilter.MDC_TRACE_ID)).isNull();
    }
}
