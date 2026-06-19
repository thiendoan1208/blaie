package com.blaie.blaie_be.core.security;

import com.blaie.blaie_be.core.error.ApiErrorResponse;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.request.RequestContextHolder;
import com.blaie.blaie_be.core.request.RequestContextFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SecurityErrorResponseWriter {
    private final ObjectMapper objectMapper;

    public SecurityErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        String requestId = RequestContextHolder.currentRequestId().orElse(null);
        response.setStatus(errorCode.status().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (requestId != null) {
            response.setHeader(RequestContextFilter.REQUEST_ID_HEADER, requestId);
        }
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiErrorResponse.of(errorCode, errorCode.defaultMessage(), null, requestId)
        );
    }
}
