package com.blaie.blaie_be.capture.application.admin.port;

import com.blaie.blaie_be.capture.application.admin.result.AdminProcessingJobMutationResult;
import java.time.Instant;
import java.util.UUID;

public interface AdminProcessingJobCommandPort {
    AdminProcessingJobMutationResult requeue(
            UUID jobId,
            UUID actorId,
            String requestId,
            Instant now
    );

    AdminProcessingJobMutationResult markDead(
            UUID jobId,
            String reason,
            UUID actorId,
            String requestId,
            Instant now
    );
}
