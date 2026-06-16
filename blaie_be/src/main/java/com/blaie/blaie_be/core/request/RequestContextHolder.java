package com.blaie.blaie_be.core.request;

import java.util.Optional;

public final class RequestContextHolder {
    private static final ThreadLocal<RequestContext> CONTEXT = new ThreadLocal<>();

    private RequestContextHolder() {
    }

    public static void set(RequestContext requestContext) {
        CONTEXT.set(requestContext);
    }

    public static Optional<RequestContext> current() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static Optional<String> currentRequestId() {
        return current().map(RequestContext::requestId);
    }

    public static RequestContext requireCurrent() {
        RequestContext requestContext = CONTEXT.get();
        if (requestContext == null) {
            throw new IllegalStateException("request context is not available");
        }
        return requestContext;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
