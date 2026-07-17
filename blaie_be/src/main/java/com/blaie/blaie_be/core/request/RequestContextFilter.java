package com.blaie.blaie_be.core.request;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component("blaieRequestContextFilter")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_HEADER = "X-Request-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        RequestContext previousContext = RequestContextHolder.current().orElse(null);
        RequestContextHolder.set(new RequestContext(requestId, request.getMethod(), request.getRequestURI(), request.getQueryString()));
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try (MdcContextScope ignored = MdcContextScope.overlay(Map.of(
                "requestId", requestId,
                "method", request.getMethod(),
                "path", request.getRequestURI()
        ))) {
            filterChain.doFilter(request, response);
        } finally {
            if (previousContext == null) {
                RequestContextHolder.clear();
            } else {
                RequestContextHolder.set(previousContext);
            }
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (RequestIdPolicy.isValid(requestId)) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }
}
