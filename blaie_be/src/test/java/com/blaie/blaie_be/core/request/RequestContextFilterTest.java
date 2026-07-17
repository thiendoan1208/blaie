package com.blaie.blaie_be.core.request;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestContextFilterTest {
    private final RequestContextFilter filter = new RequestContextFilter();

    @Test
    void validClientRequestIdIsPreserved() throws Exception {
        MockHttpServletResponse response = filter("web:request_123-abc.def");

        Assertions.assertThat(response.getHeader(RequestContextFilter.REQUEST_ID_HEADER))
                .isEqualTo("web:request_123-abc.def");
    }

    @Test
    void invalidClientRequestIdsAreReplacedWithServerUuid() throws Exception {
        assertReplaced("request id with spaces");
        assertReplaced("request\r\nid");
        assertReplaced("a".repeat(129));
        assertReplaced("réquest-id");
    }

    @Test
    void requestAndMdcContextsAreVisibleInsideTheChainAndRestoredAfterward() throws Exception {
        RequestContext previous = new RequestContext("previous-request", "TEST", "/previous", null);
        RequestContextHolder.set(previous);
        MDC.put("traceId", "upstream-trace");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/captures/text");
        request.addHeader(RequestContextFilter.REQUEST_ID_HEADER, "capture-request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, (servletRequest, servletResponse) -> {
                Assertions.assertThat(RequestContextHolder.requireCurrent().requestId())
                        .isEqualTo("capture-request-123");
                Assertions.assertThat(MDC.get("requestId")).isEqualTo("capture-request-123");
                Assertions.assertThat(MDC.get("method")).isEqualTo("POST");
                Assertions.assertThat(MDC.get("path")).isEqualTo("/api/v1/captures/text");
                Assertions.assertThat(MDC.get("traceId")).isEqualTo("upstream-trace");
            });

            Assertions.assertThat(RequestContextHolder.requireCurrent()).isSameAs(previous);
            Assertions.assertThat(MDC.getCopyOfContextMap())
                    .containsExactlyEntriesOf(java.util.Map.of("traceId", "upstream-trace"));
        } finally {
            RequestContextHolder.clear();
            MDC.clear();
        }
    }

    @Test
    void requestAndMdcContextsAreRestoredWhenTheChainFails() {
        RequestContext previous = new RequestContext("previous-request", "TEST", "/previous", null);
        RequestContextHolder.set(previous);
        MDC.put("traceId", "upstream-trace");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/captures/text");
        request.addHeader(RequestContextFilter.REQUEST_ID_HEADER, "failing-request");

        try {
            Assertions.assertThatThrownBy(() -> filter.doFilter(
                    request,
                    new MockHttpServletResponse(),
                    (servletRequest, servletResponse) -> {
                        throw new ServletException("simulated failure");
                    }
            )).isInstanceOf(ServletException.class);
            Assertions.assertThat(RequestContextHolder.requireCurrent()).isSameAs(previous);
            Assertions.assertThat(MDC.getCopyOfContextMap())
                    .containsExactlyEntriesOf(java.util.Map.of("traceId", "upstream-trace"));
        } finally {
            RequestContextHolder.clear();
            MDC.clear();
        }
    }

    private void assertReplaced(String requestId) throws Exception {
        String resolvedRequestId = filter(requestId).getHeader(RequestContextFilter.REQUEST_ID_HEADER);

        Assertions.assertThatCode(() -> UUID.fromString(resolvedRequestId)).doesNotThrowAnyException();
        Assertions.assertThat(resolvedRequestId).isNotEqualTo(requestId);
    }

    private MockHttpServletResponse filter(String requestId) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader(RequestContextFilter.REQUEST_ID_HEADER, requestId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
