package com.blaie.blaie_be.core.request;

import java.util.Map;
import org.slf4j.MDC;

public final class MdcContextScope implements AutoCloseable {
    private final Map<String, String> previousContext;
    private boolean closed;

    private MdcContextScope(Map<String, String> values, boolean replace) {
        previousContext = MDC.getCopyOfContextMap();
        if (replace) {
            MDC.clear();
        }
        values.forEach((key, value) -> {
            if (key != null && value != null) {
                MDC.put(key, value);
            }
        });
    }

    public static MdcContextScope overlay(Map<String, String> values) {
        return new MdcContextScope(Map.copyOf(values), false);
    }

    public static MdcContextScope replace(Map<String, String> values) {
        return new MdcContextScope(Map.copyOf(values), true);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (previousContext == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(previousContext);
        }
    }
}
