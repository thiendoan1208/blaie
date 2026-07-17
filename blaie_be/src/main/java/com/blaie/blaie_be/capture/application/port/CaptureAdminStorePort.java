package com.blaie.blaie_be.capture.application.port;

import com.blaie.blaie_be.capture.application.result.AdminOutboxSummaryResult;
import com.blaie.blaie_be.capture.application.result.AdminProcessingJobCursor;
import com.blaie.blaie_be.capture.application.result.AdminProcessingJobMutationResult;
import com.blaie.blaie_be.capture.application.result.AdminProcessingJobResult;
import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaptureAdminStorePort {
    List<AdminProcessingJobResult> findJobs(
            ProcessingJobStatus status,
            boolean stuck,
            AdminProcessingJobCursor cursor,
            Instant now,
            int limit
    );

    Optional<AdminProcessingJobResult> findJob(UUID jobId);

    AdminProcessingJobMutationResult requeue(UUID jobId, Instant now);

    AdminProcessingJobMutationResult markDead(UUID jobId, Instant now);

    AdminOutboxSummaryResult outboxSummary(Instant now);
}
