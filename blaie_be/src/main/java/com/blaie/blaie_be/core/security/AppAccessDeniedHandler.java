package com.blaie.blaie_be.core.security;

import com.blaie.blaie_be.core.error.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class AppAccessDeniedHandler implements AccessDeniedHandler {
    private final SecurityErrorResponseWriter errorResponseWriter;

    public AppAccessDeniedHandler(SecurityErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException, ServletException {
        errorResponseWriter.write(response, ErrorCode.FORBIDDEN);
    }
}
