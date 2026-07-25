package com.blaie.blaie_be.capture.application.admin.query;

import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;

public record AdminJobQuery(
        ProcessingJobStatus status,
        boolean stuck,
        String cursor,
        int limit
) {
    public AdminJobQuery {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
    }
}
