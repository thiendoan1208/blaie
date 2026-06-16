package com.blaie.blaie_be.core.request;

import org.springframework.util.StringUtils;

public record RequestContext(
        String requestId,
        String method,
        String path,
        String queryString
) {
    public RequestContext {
        if (!StringUtils.hasText(requestId)) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        method = StringUtils.hasText(method) ? method : "UNKNOWN";
        path = StringUtils.hasText(path) ? path : "/";
        queryString = StringUtils.hasText(queryString) ? queryString : null;
    }

    public String fullPath() {
        if (queryString == null) {
            return path;
        }
        return path + "?" + queryString;
    }
}
