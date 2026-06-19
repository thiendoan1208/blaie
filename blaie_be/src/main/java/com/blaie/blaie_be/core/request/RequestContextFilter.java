package com.blaie.blaie_be.core.request;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component("blaieRequestContextFilter")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final int MAX_REQUEST_ID_LENGTH = 128;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        RequestContextHolder.set(new RequestContext(requestId, request.getMethod(), request.getRequestURI(), request.getQueryString()));
        MDC.put("requestId", requestId);
        MDC.put("method", request.getMethod());
        MDC.put("path", request.getRequestURI());
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
            RequestContextHolder.clear();
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (isValidRequestId(requestId)) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }

    private boolean isValidRequestId(String requestId) {
        if (requestId == null || requestId.isEmpty() || requestId.length() > MAX_REQUEST_ID_LENGTH) {
            return false;
        }
        for (int index = 0; index < requestId.length(); index++) {
            char character = requestId.charAt(index);
            boolean allowed = character >= 'A' && character <= 'Z'
                    || character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9'
                    || character == '.'
                    || character == '_'
                    || character == ':'
                    || character == '-';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}
