package com.blaie.blaie_be.capture.application.admin.port;

import com.blaie.blaie_be.capture.application.admin.result.AdminProcessingJobCursor;
import com.blaie.blaie_be.capture.application.admin.result.AdminProcessingJobResult;
import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminProcessingJobQueryPort {
    List<AdminProcessingJobResult> findJobs(
            ProcessingJobStatus status,
            boolean stuck,
            AdminProcessingJobCursor cursor,
            Instant now,
            int limit
    );

    Optional<AdminProcessingJobResult> findJob(UUID jobId);
}
