package com.blaie.blaie_be.core.request;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class MdcContextScopeTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void overlayAndReplaceRestoreTheExactPreviousContext() {
        MDC.put("traceId", "upstream-trace");

        try (MdcContextScope outer = MdcContextScope.overlay(Map.of("requestId", "request-1"))) {
            assertThat(MDC.get("traceId")).isEqualTo("upstream-trace");
            assertThat(MDC.get("requestId")).isEqualTo("request-1");

            try (MdcContextScope inner = MdcContextScope.replace(Map.of("jobId", "job-1"))) {
                assertThat(MDC.get("traceId")).isNull();
                assertThat(MDC.get("requestId")).isNull();
                assertThat(MDC.get("jobId")).isEqualTo("job-1");
            }

            assertThat(MDC.get("traceId")).isEqualTo("upstream-trace");
            assertThat(MDC.get("requestId")).isEqualTo("request-1");
            assertThat(MDC.get("jobId")).isNull();
        }

        assertThat(MDC.getCopyOfContextMap()).containsExactlyEntriesOf(Map.of("traceId", "upstream-trace"));
    }
}
