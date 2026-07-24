package com.blaie.blaie_be.core.error;

import com.blaie.blaie_be.core.request.RequestContext;
import com.blaie.blaie_be.core.request.RequestContextHolder;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @BeforeEach
    void setUpRequestContext() {
        RequestContextHolder.set(new RequestContext(
                "safe-request-id",
                "GET",
                "/api/v1/admin/unknown",
                null
        ));
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.clear();
    }

    @Test
    void mapsUnknownApiResourceToSafeJson404() {
        var response = handler.handleNoResourceFoundException(
                new NoResourceFoundException(HttpMethod.GET, "api/v1/admin/unknown", "")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                "NOT_FOUND",
                "Not found",
                null,
                "safe-request-id"
        ));
        assertThat(response.getHeaders().getFirst("X-Request-ID")).isEqualTo("safe-request-id");
    }

    @Test
    void mapsUnsupportedMethodToSafeJson405AndPreservesAllowHeader() {
        var response = handler.handleMethodNotSupportedException(
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "POST"))
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                "METHOD_NOT_ALLOWED",
                "Method not allowed",
                null,
                "safe-request-id"
        ));
        assertThat(response.getHeaders().getAllow())
                .containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST);
    }
}
