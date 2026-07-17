package com.blaie.blaie_be.capture.application.result;

import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;

public record AdminProcessingJobMutationResult(
        ProcessingJobStatus previousStatus,
        AdminProcessingJobResult job
) {
}
