package com.blaie.blaie_be.capture.application.result;

import java.util.List;

public record AdminProcessingJobPageResult(
        List<AdminProcessingJobResult> jobs,
        String nextCursor,
        boolean hasMore,
        int limit
) {
    public AdminProcessingJobPageResult {
        jobs = List.copyOf(jobs);
    }
}
