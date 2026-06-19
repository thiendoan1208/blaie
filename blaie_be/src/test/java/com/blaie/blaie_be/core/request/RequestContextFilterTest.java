package com.blaie.blaie_be.core.request;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
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
