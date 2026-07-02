package com.blaie.blaie_be.core.ratelimit.support;

import com.blaie.blaie_be.core.ratelimit.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {
    private final RateLimitProperties properties;

    public ClientIpResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        if (properties.useForwardedHeaders()) {
            String forwarded = firstForwardedFor(request.getHeader("Forwarded"));
            if (forwarded != null) {
                return forwarded;
            }
            String xForwardedFor = firstCsvValue(request.getHeader("X-Forwarded-For"));
            if (xForwardedFor != null) {
                return xForwardedFor;
            }
            String xRealIp = clean(request.getHeader("X-Real-IP"));
            if (xRealIp != null) {
                return xRealIp;
            }
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private String firstForwardedFor(String header) {
        String first = firstCsvValue(header);
        if (first == null) {
            return null;
        }
        for (String part : first.split(";")) {
            String trimmed = part.trim();
            if (trimmed.toLowerCase(java.util.Locale.ROOT).startsWith("for=")) {
                return clean(trimmed.substring(4).replace("\"", ""));
            }
        }
        return null;
    }

    private String firstCsvValue(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        return clean(header.split(",", 2)[0]);
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
